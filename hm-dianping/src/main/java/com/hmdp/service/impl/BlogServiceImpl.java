package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.conditions.update.UpdateChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IFollowService followService;

    /**
     * 查询最热博文
     * @param current
     * @return
     */
    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    /**
     * 点赞排行榜
     * @param id
     * @return
     */
    @Override
    public Result queryBlogLikes(Long id) {
        //查询前五点赞用户
        Set<String> top5Id = stringRedisTemplate.opsForZSet().range(RedisConstants.BLOG_LIKED_KEY + id, 0, 4);
        if(top5Id == null || top5Id.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        //解析出其中的id
        List<Long> ids = top5Id.stream().map(Long::valueOf).collect(Collectors.toList());
        //根据用户id查询用户
        String idStr = StrUtil.join(",", ids);
        List<UserDTO> dtos = userService.query().in("id", ids).last("order by field(id," + idStr + ")" ).list().stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());
        //返回
        return Result.ok(dtos);
    }

    @Override
    public Result saveBlog(Blog blog) {
        //获取当前用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        //保存探店博文
        boolean isSuccess = save(blog);
        if(!isSuccess){
            return Result.fail("新增笔记失败");
        }
        //查询作者的粉丝
        List<Follow> follows = followService.query().eq("follow_user_id", user.getId()).list();
        for(Follow follow : follows){
            //遍历获取粉丝id
            Long userId = follow.getUserId();
            //推送笔记
            String key = RedisConstants.FEED_KEY + userId;
            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
        }
        //返回id
        return Result.ok(blog.getId());
    }

    /**
     * 查询收件箱滚动分页
     * @param max
     * @param offset
     * @return
     */
    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        //1。获取当前用户
        Long userId = UserHolder.getUser().getId();
        String key = RedisConstants.FEED_KEY + userId;
        //2.查询收件箱
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet().reverseRangeByScoreWithScores(key, 0, max, offset, 3);

        if(typedTuples == null || typedTuples.isEmpty()){
            return Result.ok();
        }
        //3.解析数据：blogId  score（时间戳） offset
        List<Long> ids = new ArrayList<>(typedTuples.size());
        Long minTime = 0L;
        int os = 1;
        for(ZSetOperations.TypedTuple<String> tuple : typedTuples){

            //获取id
            String value = tuple.getValue();
            ids.add(Long.valueOf(value));
            //时间戳
            long time = tuple.getScore().longValue();
            if(time == minTime){
                os++;
            }else{
                minTime = time;
                os = 1;
            }
        }

        //4.根据id查询blog
        String idStr = StrUtil.join(",", ids);
        List<Blog> blogs = query().in("id", ids).last("order by field(id," + idStr + ")" ).list();

        for(Blog blog : blogs){
            queryBlogUser(blog);
            isBlogLiked(blog);
        }
        //5.封装并返回
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogs);
        scrollResult.setOffset(os);
        scrollResult.setMinTime(minTime);
        return Result.ok(scrollResult);
    }

    private void isBlogLiked(Blog blog) {
        //获取登录用户
        UserDTO user = UserHolder.getUser();
        if(user == null){
            //未登录，无需查询是否点赞
            return;
        }
        //获取当前用户
        Long userId = user.getId();
        //判断当前登录用户是否已经点过赞
        String key = RedisConstants.BLOG_LIKED_KEY + blog.getId();
        Double isMember = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(isMember != null);
    }

    /**
     * 查询博文详情
     * @param id
     * @return
     */
    @Override
    public Result queryBlogId(Long id) {
        //1.查询blog
        Blog blog = getById(id);
        //2.查询blog有关的用户
        if(blog == null){
            return Result.fail("笔记不存在！");
        }
        queryBlogUser(blog);
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setIcon(user.getIcon());
        blog.setName(user.getNickName());
    }



    /**
     * 笔记点赞
     * @param id
     * @return
     */
    @Override
    public Result likeBlog(Long id) {
        //获取当前用户
        Long userId = UserHolder.getUser().getId();
        //判断当前登录用户是否已经点过赞
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        Double isMember = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if(isMember == null){
            //如果未点赞.数据库点赞
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            //缓存用户点赞数据到redis的set集合中数+1
            if(isSuccess){
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
        }else{
            //如果已经点赞，再点击取消点赞
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            //把用户redis中移除
            stringRedisTemplate.opsForZSet().remove(key, userId.toString());
        }

        return Result.ok();
    }


}
