package cn.yecaiyu;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CxApplication {
    public static void main(String[] args) {
        SpringApplication.run(CxApplication.class, args);
        // ChxAccount account = new ChxAccount("用户名", "密码");
        // ChaoXing chaoXing = new ChaoXing();
        // chaoXing.doWork(account);
    }
}
