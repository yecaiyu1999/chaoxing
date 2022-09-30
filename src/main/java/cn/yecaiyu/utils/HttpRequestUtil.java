package cn.yecaiyu.utils;

import cn.hutool.crypto.digest.MD5;
import cn.hutool.http.Header;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;

import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;

public class HttpRequestUtil {

    public static HttpResponse http(HttpRequest http, String cookie, String host, String origin, String referer, Map<String, Object> params) {
        Random random = new Random();


        http.header(Header.COOKIE, cookie)
                .header(Header.USER_AGENT, "Dalvik/2.1.0 (Linux; U; Android " + (random.nextInt(3) + 9) + "; MI" + (random.nextInt(2) + 10) + " Build/SKQ1.210216.001) (device:MI" + (random.nextInt(2) + 10) + ") Language/zh_CN com.chaoxing.mobile/ChaoXingStudy_3_5.1.4_android_phone_614_74 (@Kalimdor)_"+ MD5.create().digestHex(UUID.randomUUID().toString()))
                .header("X-Requested-With", "com.chaoxing.mobile");

        if (!Objects.isNull(host)) {
            http.header(Header.HOST, host);
        }

        if (!Objects.isNull(origin)) {
            http.header(Header.ORIGIN, origin);
        }

        if (!Objects.isNull(referer)) {
            http.header(Header.REFERER, referer);
        }


        if (!Objects.isNull(params)) {
            http.form(params);
        }

        return http.execute();
    }

    public static HttpResponse get(String url, String cookie, String host, String origin, String referer, Map<String, Object> params) {
        return http(HttpRequest.get(url), cookie, host, origin, referer, params);
    }

    public static HttpResponse post(String url, String cookie, String host, String origin, String referer, Map<String, Object> params) {
        return http(HttpRequest.post(url), cookie, host, origin, referer, params);
    }

}
