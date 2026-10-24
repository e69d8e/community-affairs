package com.li.community;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.li.community.mapper")
@EnableScheduling
@EnableAsync
public class CommunityAffairsApplication {
    public static void main(String[] args) {
        SpringApplication.run(CommunityAffairsApplication.class, args);
    }
}
