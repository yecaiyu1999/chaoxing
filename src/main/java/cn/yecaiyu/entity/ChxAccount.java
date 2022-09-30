package cn.yecaiyu.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class ChxAccount {
    private String username;
    private String password;

    private String userid;

    private String cookie;

    private List<ChxCourse> allCourse;

    private ChxCourse curCourse;

    private Boolean isRunning;

    public ChxAccount(String username, String password) {
        this.username = username;
        this.password = password;
    }

}