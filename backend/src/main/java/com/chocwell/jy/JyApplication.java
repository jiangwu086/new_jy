package com.chocwell.jy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableTransactionManagement
public class JyApplication {

    public static void main(String[] args) {
        SpringApplication.run(JyApplication.class, args);
        System.out.println("====== 新就业形态劳动者安全警示服务后端启动成功 ======");
    }
}
