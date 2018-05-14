package com.av.dao;

import java.util.Date;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import javax.annotation.Resource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;


@Repository
public class VotingDao {
	
	@Autowired
	private RedisTemplate<String, Object> redisTemplate = new RedisTemplate<String, Object>();
	
	public void set(String key, String value){
		redisTemplate.opsForValue().set(key, value);
	}

}
