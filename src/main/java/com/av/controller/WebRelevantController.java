package com.av.controller;


import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.SortParameters.Order;
import org.springframework.data.redis.connection.jedis.JedisConnection;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.BulkMapper;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.data.redis.core.query.SortQuery;
import org.springframework.data.redis.core.query.SortQueryBuilder;
import org.springframework.data.redis.hash.DecoratingStringHashMapper;
import org.springframework.data.redis.hash.HashMapper;
import org.springframework.data.redis.hash.JacksonHashMapper;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.av.dao.VotingDao;
import com.av.entity.DeviceInfo;
import com.av.entity.InspecterInfo;
import com.av.entity.UserToken;
import com.av.util.AESUtil;
import com.google.gson.Gson;

import ch.qos.logback.core.util.ExecutorServiceUtil;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Transaction;

@RestController
@RequestMapping("/wr")
public class WebRelevantController {
	
	private static Logger logger = LoggerFactory.getLogger(WebRelevantController.class);
	private final int TIME_OUT = 10;
	
	@Autowired
	private RedisTemplate<String, Object> redisTemplate;
	@Autowired
	private JedisConnectionFactory factory;
	
	
	
	@Autowired
	private HttpServletRequest request;
	
	private final String WEB_NAME = "公司名";
	
	private Gson gson = new Gson();
	private static HashMap<String, String> useridMap;
	
	static{
		useridMap = new HashMap<>();
		useridMap.put("zhangsan", "a");
	}
	
	private final String loginHash = "loginHash";// 存放token--用户相关信息对应关系的散列
	
	
	SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	
	/**
	 * 登录获取令牌
	 */
	@RequestMapping(value = "/login", method = RequestMethod.GET)
	public String login(String username, String password){
		String msg = "";
		
		if(username.equals("zhangsan") && password.equals("1")){// 假设登录通过
			String ip = request.getRemoteAddr();
			String userRole = UUID.randomUUID().toString();
			String token = AESUtil.encrypt(ip+"|"+userRole, WEB_NAME);
			LinkedList<String> eles = new LinkedList<>();
			eles.add("login");
			// 保存相关信息
			redisTemplate.opsForHash().put("loginHash:"+token, "userid", useridMap.get(username));
			redisTemplate.opsForHash().put("loginHash:"+token, "lasttime", System.currentTimeMillis()+"");
			redisTemplate.opsForHash().put("loginHash:"+token, "elementids", gson.toJson(eles));
			// 设置token过期时间
			redisTemplate.expire("loginHash:"+token, TIME_OUT, TimeUnit.MINUTES);
			logger.info("token:"+token);
		}
		
		return msg;
	}
	
	/**
	 * 令牌cookie情况下使用redis代替关系型数据库存储cookie相关数据
	 * 为什么要替换，因为假设并发量很高
	 */
	@RequestMapping(value = "/checkToken", method = RequestMethod.GET)
	public String checkToken(String token){// 注意：token里可能含有+号，空格等特殊字符，所以请求的时候参数需要encode
		String msg = "";
		
		// 根据token获取相关信息
		if (msg.equals("")) {
			Object o = redisTemplate.opsForHash().get("loginHash:"+token, "userid");
			if (o == null) {
				msg = "get token info error";
			}
			if (msg.equals("")) {
				logger.info("userid:"+(String)o);
			}
		}
		
		return msg;
	}
	
	/**
	 * 更新令牌信息
	 * 只要用户操作了系统，就要调用该接口
	 * @param elementid 用户操作的哪个页面元素
	 */
	@RequestMapping(value = "/updateToken", method = RequestMethod.GET)
	public String updateToken(String token, String userid, String elementid){
		String msg = "";
		String token_userid = "";
		Object o = null;
		// 获取最新的时间戳
		long time = System.currentTimeMillis();
		
		UserToken userToken = null;
		if(msg.equals("")){
			o = redisTemplate.opsForHash().get("loginHash:"+token, "userid");
			if (o == null) {
				msg = "token is not exists";
			}
		}
			
		if(msg.equals("")){
			token_userid = (String)o;
			if (userToken.getUserId().equals(userid)) {
				msg = "token is not relation user";
			}
		}	
		
		if(msg.equals("")){
			// 给这个token重新设置10分钟后过期
			redisTemplate.expire("loginHash:"+token, TIME_OUT, TimeUnit.MINUTES);
		}
		
		if(msg.equals("")){
			// 更新token里的时间
			redisTemplate.opsForHash().put("loginHash:"+token, "lasttime", System.currentTimeMillis());

			// 添加进新的页面操作元素
			Object eleObj = redisTemplate.opsForHash().get("loginHash:"+token, "elementids");
			LinkedList<String> elids = null;
			if (eleObj == null) {
				elids = new LinkedList<>();
			}else{
				String eleidstr = (String) eleObj;
				elids = gson.fromJson(eleidstr, LinkedList.class);
			}
				
			
			boolean addbol = elids.add(elementid);
			if (!addbol) {
				msg = "add user operation element error";
			}
			
			if (msg.equals("")) {
				redisTemplate.opsForHash().put("loginHash:"+token, "elementids", gson.toJson(elids));
			}
			
		}
		
		return msg;
	}
	
	/**
	 * 测试redis的排序和分页
	 */
	@RequestMapping(value = "/sort", method = RequestMethod.GET)
	public String sort(){
		String msg = "";
		
		final String LIST_KEY_NAME = "listUsebySort";
		// 创建redis list，并且赋值，采用左进右出
//		redisTemplate.opsForList().leftPushIfPresent(LIST_KEY_NAME, value);
		Set<String> keyset = redisTemplate.keys(LIST_KEY_NAME);
		if (keyset == null || keyset.size() <= 0) {// 先判断key是否已存在，存在就不设置了
			String[] ls = new String[]{"beijing","shanghai","gungzhou","shenzheng","wuxi","xiamen","taiwn","wuhan","sichuan"};
			Long plong = redisTemplate.opsForList().rightPushAll(LIST_KEY_NAME, ls);
			redisTemplate.expire(LIST_KEY_NAME, 500, TimeUnit.MINUTES);
			
			if (plong < 1) {
				msg = "Failure of initialization list";
			}
		}
		
		if (msg.equals("")) {
//		    SortQuery<String> query = SortQueryBuilder.sort("test-user-1").noSort().get("#").get("test-map-*->uid").get("test-map-*->content").build(); 
			SortQuery<String> query = SortQueryBuilder.sort(LIST_KEY_NAME)// 排序的key
					//.by("pattern")       key的正则过滤
					.noSort()            //不使用排序  反之就是使用排序
//					.get("#")            //#代表是查询   可以连续写多个get
//					.get("shenzheng") 
					.limit(0, 5)         //分页，和mysql一样
					.order(Order.DESC)   //正序or倒序
					.alphabetical(true)  //ALPHA修饰符用于对字符串进行排序，false的话只针对数字排序 
					.build();
			
			BulkMapper<UserToken, Object> hm = new BulkMapper<UserToken, Object>() {
				@Override
				public UserToken mapBulk(List<Object> bulk) {// 实现BulkMapper接口的方法，来把获取到的排序的数据转换为我们需要的返回类型
					Iterator<Object> iterator = bulk.iterator();  
					UserToken userToken = new UserToken();
					userToken.setUserId((String)iterator.next());
					userToken.setLastTime((long)iterator.next());
					userToken.setElementids((LinkedList<String>)iterator.next());
					return userToken;
				}
			};  
			
			List<Object> olist = redisTemplate.sort(query);
			for (Object object : olist) {
				logger.info("city:"+(String)object);
			}
//			redisTemplate.sort(query, hm);
		}
		
		
		return msg;
	}
	
	/**
	 * 测试redis的事务操作（放弃使用redis事务）
	 * @throws InterruptedException 
	 */
	@RequestMapping(value = "/trans", method = RequestMethod.GET)
	public String trans() throws InterruptedException{
		String msg = "";
		
		final String KEY_NAME = "trans";
		JedisConnection connection = (JedisConnection) factory.getConnection();
		
		// 当对某个key进行watch后如果其他的客户端对这个key进行了更改，那么本次事务会被取消，事务的exec会返回null
//		connection.watch(KEY_NAME.getBytes());
		// 谈谈对redis事务的理解
		// redis的事务和其它关系型数据库的事务概念不是太一样，redis事务不支持回滚，并且一条命令出错后，后面的命令还会执行。
		
		
//		connection.multi();
//		connection.openPipeline();
		
		redisTemplate.opsForValue().set(KEY_NAME, "test1", 10, TimeUnit.MINUTES);
		logger.info("set test1");
		

		Thread.sleep(10000);
		
		
		redisTemplate.opsForValue().append(KEY_NAME, "test33");
		logger.info("set test33");
		
//		connection.discard();
//		
//		redisTemplate.opsForValue().append(KEY_NAME, "test44");
//		logger.info("set test44");

//		connection.closePipeline();
//		connection.exec();
		
		
		return msg;
	}
	
	/**
	 * 100条数据一传
	 * @throws InterruptedException 
	 * 
	 */
	@RequestMapping(value = "/xx", method = RequestMethod.GET)
	public String xx() throws InterruptedException{
		
		String msg = "";
		
		ArrayBlockingQueue<String> dataQueue = new ArrayBlockingQueue<String>(1000); 
		
		ThreadFactory threadFactory = Executors.defaultThreadFactory();
		
		ExecutorService executorService = Executors.newFixedThreadPool(3,threadFactory);
		
		// push thread
		executorService.submit(new Callable<String>() {
			@Override
			public String call() {
				try {
					for (int i = 0; i < 5000; i++) {
						dataQueue.put(gson.toJson(getData()));
						Thread.sleep(200);// Get ready data
						logger.info("push "+(i+1)+" data");
						if (i == 50) {
							throw new NullPointerException();
						}
					}
				} catch (Exception e) { // 需要捕获异常，不然出错该线程会断掉
					logger.info("push exception: "+ e.getMessage());
					e.printStackTrace();
				}
				
				return "push data over";
			}
		});
		
		// pop thread
		executorService.submit(new Callable<String>() {
			@Override
			public String call() throws Exception {
				try {
					for (int i = 0; i < 5000; i++) {
						String data = dataQueue.take();
						Thread.sleep(800);// moni ruku
						logger.info("pop data \r\n"+data.substring(0,20));
						
					}
				} catch (Exception e) { // 需要捕获异常，不然出错该线程会断掉
					logger.info("pop exception: "+ e.getMessage());
					e.printStackTrace();
				}
				
				return "pop data over";
			}
		});
		
		// damon thread
		executorService.execute(new Runnable() {
			
			@Override
			public void run() {
				while (true) {
					
					logger.info("queue size:"+dataQueue.size());
					
					try {
						Thread.sleep(3000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					
//					if (dataQueue.isEmpty()) {
//						logger.info("queue is empty, while break...");
//						break;
//					}
				}
			}
		});
		
		executorService.shutdown();
		
		return msg;
	}
	
	public List<DeviceInfo> getData(){
		
		List<DeviceInfo> deviceinfos = new ArrayList<>();
		
		InspecterInfo inspecter = null;
		DeviceInfo device = null;
		for (int i = 0; i < 100; i++) {
			
			inspecter = new InspecterInfo();
			inspecter.setId(UUID.randomUUID().toString().replaceAll("-", ""));
			inspecter.setRealName("r__r__r");
			inspecter.setSex("man");
			inspecter.setAge(18);
			inspecter.setHasInsQuali(true);
			
			device = new DeviceInfo();
			device.setId(UUID.randomUUID().toString().replaceAll("-", ""));
			device.setDeviceNumber("3120"+UUID.randomUUID().toString().replaceAll("-", "").substring(0, 6));
			device.setInspectTime(System.currentTimeMillis());
			device.setDeviceType("lift");
			device.setInspecter(inspecter);
			
			deviceinfos.add(device);
			
			inspecter = null;
			device = null;
		}
		
		return deviceinfos;
	}
	
	
	public static void main(String[] args) {
		int flag = 3;
		Object lock = new Object();
		
		
		Thread t1 = new Thread(new Runnable() {
			@Override
			public void run() {
				System.out.println("t1");
				try {
					Thread.sleep(8000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		});

		t1.start();
		
		System.out.println(t1.isInterrupted());
		t1.interrupt();
		System.out.println(t1.isInterrupted());
		
//		t1.interrupted();
		

		
	}

		
		
		
	
	
	
}
