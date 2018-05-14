package com.av.controller;


import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.av.dao.VotingDao;

@RestController
@RequestMapping("/av")
public class ArticleVoteController {
	
	private static Logger logger = LoggerFactory.getLogger(ArticleVoteController.class);

	@Value("${custom.random.articleid}")
	private String articleid_random;
	
	@Autowired
	private RedisTemplate<String, Object> redisTemplate;
	
	private String CHANGELESS_TIME = "2018-04-01 00:00:00"; 
	private final int TIME_OUT = 10;
	
	private final String articleHash = "articleHash";// 存放文章各个组成部分的hash的key
	private final String alreadyVotedUserIdSet = "alreadyVotedUserIdSet";// 某文章已投票用户集合的key,key后面要加上文章id
	private final String articleIdTimeZSet = "articleIdTimeZSet";// 发布文章按时间排序的有序集合
	private final String articleIdScoreZSet = "articleIdScoreZSet";// 发布文章按评分排序的有序集合
	private final String articleGroupZSet = "articleGroupZSet";// 文章所属分组
	private final String groupSet = "groupSet";// 分组集合
	
	
	SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	/**
	 * 发布文章
	 */
	@RequestMapping(value = "/releaseArticle", method = RequestMethod.GET)
	public String releaseArticle(){
		String msg = "";
		
		// 这边假设每篇文章可以有两个分组
		String[] groups = new String[]{"xiaoshuo","sanwen","lunwen"};
		String[] groups1 = new String[]{"1000zi","5000zi","10000zi"};
		String group = groups[new Random().nextInt(3)];// 文章所属分组
		String group1 = groups1[new Random().nextInt(3)];// 文章所属分组
		
		String articleid = UUID.randomUUID().toString().substring(0,8);
		String title = "title"+UUID.randomUUID().toString().substring(0,8);
		Date d = new Date();
		String time = sdf.format(d);
		int num = 0;// 文章投票数量
		
		// 添加文章信息进散列
		if (msg.equals("")) {
			Map<String, Object> articleMap = new HashMap<String, Object>();
			articleMap.put("title|"+articleid, title);
			articleMap.put("id|"+articleid, articleid);
			articleMap.put("time|"+articleid, time);
			articleMap.put("num|"+articleid, num+"");// 文章投票数量
			redisTemplate.opsForHash().putAll(articleHash, articleMap);
		}
		
		// 将文章id添加进已投票用户集合,此时value，也就是用户为null
		if (msg.equals("")) {
			Long addlong = redisTemplate.opsForSet().add(alreadyVotedUserIdSet+articleid, "");
			Boolean expirebol = redisTemplate.expire(alreadyVotedUserIdSet+articleid, 7, TimeUnit.DAYS);// 设置超时时间，因为文章一周后不再让投票
			if (addlong < 1 || expirebol == false) {
				msg = "给文章初始化已投票用户集合失败";
			}
		}
		
		// 将文章发布时间添加进有序集合里
		if (msg.equals("")) {
			long newsize = 0;
			
			// 先获取到articleIdTimeZSet分数最大的value，该value+1就是新的分数
			Long size = redisTemplate.opsForZSet().size(articleIdTimeZSet);
			if (size == null || size == 0) {
				newsize = 0;
			}else{
				newsize = size+1;
			}
			
			// 获取指定区间的
			Boolean addbol = redisTemplate.opsForZSet().add(articleIdTimeZSet, articleid+":"+time, newsize);
			if (addbol == false) {
				msg = "将文章发布时间添加进有序集合里失败";
			}
		}
		
		// 将文章得分添加进有序集合里
		if (msg.equals("")) {
			Boolean addbol = redisTemplate.opsForZSet().add(articleIdScoreZSet, articleid, 0);
			if (addbol == false) {
				msg = "将文章得分添加进有序集合里失败";
			}
		}
		
		// 将文章添加进分组的有序集合里（以时间为排序分）
		// 因为文章一周前的文章不可能再排到前面了，所以这里的score是拿当前时间减去一个固定的时间，剩下的分钟再除以100
		// 实际开发中除非所有的分组集合都清空，这个CHANGELESS_TIME才可以改变，不然会导致排序错误
		if (msg.equals("")) {
			double score = 0;
			try {
				score = TimeUnit.MILLISECONDS.toSeconds(d.getTime()-sdf.parse(CHANGELESS_TIME).getTime())/100;
			} catch (ParseException e) {
				logger.error("将文章得分添加进有序集合(发布时间)里失败");
				e.printStackTrace();
			}
			Boolean addbol = redisTemplate.opsForZSet().add(group+"|time", articleid, score);// articleGroupZSet+"|"+
			Boolean addbol1 = redisTemplate.opsForZSet().add(group1+"|time", articleid, score);// articleGroupZSet+"|"+
			if (addbol == false || addbol1 == false) {
				msg = "将文章得分添加进有序集合(发布时间)里失败";
			}
		}
		
		
		// 查看分组是否已存在，不存在则保存
		if (msg.equals("")) {
			if(!redisTemplate.opsForSet().isMember(groupSet, group)){
				long addlong = redisTemplate.opsForSet().add(groupSet, group);
				if (addlong != 1) {
					msg = "添加分组失败";
				}
			}
			if (msg.equals("")) {
				if(!redisTemplate.opsForSet().isMember(groupSet, group1)){
					long addlong = redisTemplate.opsForSet().add(groupSet, group1);
					if (addlong != 1) {
						msg = "添加分组失败";
					}
				}
			}
		}
		
		return msg;
	}
	
//	@Autowired
//	private VotingDao votingDao;
	
	/**
	 * 投票
	 * @param userid 投票人
	 * @param articleid 文章
	 * @return
	 */
	@RequestMapping(value = "/vote", method = RequestMethod.GET)
	public String vote(String userid, String articleid){
		
		String msg = "";
		
		try {
			// 判断文章发布时间是否过一周，过了就不能投票
			logger.info("判断文章发布时间是否过一周");
			String time = (String) redisTemplate.opsForHash().get(articleHash, "time|"+articleid);
			if(getOneWeekLaterDate(sdf.parse(time)).before(new Date())){// 超过一周
				msg = "文章发布已超过一周，不能再投票";
			}
			
			// 判断是否是第一次投票
			if (msg.equals("")) {
				logger.info("判断是否是第一次投票");
				Boolean alreadyVotedFlag = redisTemplate.opsForSet().isMember(alreadyVotedUserIdSet, userid);
				// true 已经投过票      false 未投过票
				if (alreadyVotedFlag) {
					msg = "您已给该文章投过票，不能再投票";
				}
			}
			
			// 添加用户进文章已投票集合
			if (msg.equals("")) {
				logger.info("添加用户进文章已投票集合");
				Long addlong = redisTemplate.opsForSet().add(alreadyVotedUserIdSet+articleid, userid);
				if (addlong == null || addlong < 1) {
					msg = "添加用户投票文章记录失败";
				}
			}
			
			// 给文章评分增加432分
			if (msg.equals("")) {
				logger.info("给文章评分增加432分");
				Double score = redisTemplate.opsForZSet().incrementScore(articleIdScoreZSet, articleid, 432);
				logger.info("文章"+articleid+"的分数现在为："+score);
			}
			
			// 给散列里的文章投票数量加1
			if (msg.equals("")) {
				logger.info("给散列里的文章投票数量加1");
				Long nlong = redisTemplate.opsForHash().increment(articleHash, "num|"+articleid, 1);
				logger.info("文章"+articleid+"的投票数量现在为："+nlong);
			}
			
			
		} catch (ParseException e) {
			msg = "出错，详见日志";
			logger.error(e.getMessage());
		}
		
		
		return msg;
	}

	/**
	 * 获取评分前100的文章
	 * @return
	 */
	@RequestMapping(value = "/hotArticleScore", method = RequestMethod.GET)
	public String hotArticleScore(){
		StringBuffer sb = new StringBuffer("");
		Long size = redisTemplate.opsForZSet().size(articleIdScoreZSet);
		if (size != null && size > 0) {
			Set<TypedTuple<Object>> scoreSet = redisTemplate.opsForZSet().rangeWithScores(articleIdScoreZSet,size-100,size);
			Iterator<ZSetOperations.TypedTuple<Object>> iterator = scoreSet.iterator();
	        while (iterator.hasNext()) {
	            ZSetOperations.TypedTuple<Object> typedTuple = iterator.next();
	            sb.append("value:" + typedTuple.getValue() + "score:" + typedTuple.getScore()+"\n");
	        }
		}else{
			sb.append("没有获取到热门文章列表");
		}
		
		return sb.toString();
	}
	
	/**
	 * 获取最新发布的100的文章
	 * @return
	 */
	@RequestMapping(value = "/hotArticleTime", method = RequestMethod.GET)
	public String hotArticleTime(){
		StringBuffer sb = new StringBuffer("");
		Long size = redisTemplate.opsForZSet().size(articleIdTimeZSet);
		if (size != null && size > 0) {
			Set<TypedTuple<Object>> scoreSet = redisTemplate.opsForZSet().rangeWithScores(articleIdTimeZSet,size-100,size);
			Iterator<ZSetOperations.TypedTuple<Object>> iterator = scoreSet.iterator();
	        while (iterator.hasNext()) {
	            ZSetOperations.TypedTuple<Object> typedTuple = iterator.next();
	            sb.append("value:" + typedTuple.getValue() + "score:" + typedTuple.getScore()+"\n");
	        }
		}else{
			sb.append("没有获取到最新发布文章列表");
		}
		
		return sb.toString();
	}
	
	/**
	 * 某个分组里按时间由近到远的前10条记录
	 * @return
	 */
	@RequestMapping(value = "/groupArticles", method = RequestMethod.GET)
	public String groupArticles(String group){
		StringBuffer sb = new StringBuffer("");
		Long size = redisTemplate.opsForZSet().size(articleGroupZSet);
		if (size != null && size > 0) {
			Set<TypedTuple<Object>> scoreSet = redisTemplate.opsForZSet().rangeWithScores(articleGroupZSet,size-10,size);
			Iterator<ZSetOperations.TypedTuple<Object>> iterator = scoreSet.iterator();
	        while (iterator.hasNext()) {
	            ZSetOperations.TypedTuple<Object> typedTuple = iterator.next();
	            sb.append("value:" + typedTuple.getValue() + "score:" + typedTuple.getScore()+"\n");
	        }
		}else{
			sb.append("没有获取到分组"+group+"的最新发布文章列表");
		}
		
		return sb.toString();
	}
	
	/**
	 * 某几(这里定为两个)个不同分组里按时间由近到远的前10条记录
	 * intersectAndStore 获取有序集合的交集
	 * @return
	 */
	@RequestMapping(value = "/intersectGroupArticles", method = RequestMethod.GET)
	public String intersectGroupArticles(String groups){
		Long a = redisTemplate.opsForZSet().intersectAndStore(groups.split(",")[0]+"|time", groups.split(",")[1]+"|time", "temporary|time");
		StringBuffer sb = new StringBuffer("");
		Long size = redisTemplate.opsForZSet().size("temporary|time");
		if (size != null && size > 0) {
			Set<TypedTuple<Object>> scoreSet = redisTemplate.opsForZSet().rangeWithScores("temporary|time",size-10,size);
			Iterator<ZSetOperations.TypedTuple<Object>> iterator = scoreSet.iterator();
	        while (iterator.hasNext()) {
	            ZSetOperations.TypedTuple<Object> typedTuple = iterator.next();
	            sb.append("value:" + typedTuple.getValue() + "score:" + typedTuple.getScore()+"\n");
	        }
		}else{
			sb.append("没有获取到两个分组的最新发布文章列表");
		}
		
		return sb.toString();
	}
	
	public Date getOneWeekLaterDate(Date date){
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		calendar.set(Calendar.DAY_OF_MONTH,calendar.get(Calendar.DAY_OF_MONTH)+7);
		return calendar.getTime();
	}
	
	
}
