package com.av.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPoolConfig;

@Configuration  
@EnableAutoConfiguration 
@EnableConfigurationProperties(RedisProperties.class)// 对RedisProperties执行自动绑定属性值
public class RedisConfig {
	private static Logger logger = LoggerFactory.getLogger(RedisConfig.class);  
	  
	@Autowired
    private RedisProperties redisProperties;
  
  
    /** 
     * @Bean 和 @ConfigurationProperties 
     * 该功能在官方文档是没有提到的，我们可以把@ConfigurationProperties和@Bean和在一起使用。 
     * 举个例子，我们需要用@Bean配置一个Config对象，Config对象有a，b，c成员变量需要配置， 
     * 那么我们只要在yml或properties中定义了a=1,b=2,c=3， 
     * 然后通过@ConfigurationProperties就能把值注入进Config对象中 
     * @return 
     */  
    @Bean  
    @ConfigurationProperties(prefix = "spring.redis.pool")  
    public JedisPoolConfig getRedisConfig() {  
        JedisPoolConfig config = new JedisPoolConfig(); 
        return config;  
    }  
  
    @Bean  
    @ConfigurationProperties(prefix = "spring.redis")  
    public JedisConnectionFactory getConnectionFactory() {  
        JedisConnectionFactory factory = new JedisConnectionFactory();  
        factory.setUsePool(true);  
        JedisPoolConfig config = getRedisConfig();  
        factory.setPoolConfig(config);  
        logger.info("JedisConnectionFactory bean init success...");  
        return factory;  
    }  
    
    @Bean  
    public RedisTemplate<?, ?> getRedisTemplate() {  
        JedisConnectionFactory factory = getConnectionFactory();  
        logger.info(redisProperties.getHost()+","+factory.getHostName()+","+factory.getDatabase());  
        logger.info(redisProperties.getPassword()+","+factory.getPassword());  
        logger.info("MaxIdle:"+factory.getPoolConfig().getMaxIdle());  
//        factory.setHostName(this.host);  
//        factory.setPassword(this.password);  
        RedisTemplate<?, ?> template = new StringRedisTemplate(getConnectionFactory());  
        return template;  
    } 
    
    /**
     * 自定义key的生成策略
     * @return
     */
    /*@Bean
    public KeyGenerator myKeyGenerator(){
        return new KeyGenerator() {
            public Object generate(Object target, Method method, Object... params) {
                StringBuilder sb = new StringBuilder();
                sb.append(target.getClass().getName());
                sb.append(method.getName());
                for (Object obj : params) {
                    sb.append(obj.toString());
                }
                return sb.toString();
            }
        };
    }*/
}
