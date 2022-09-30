package cn.yecaiyu.task;

import cn.hutool.http.HttpResponse;
import cn.yecaiyu.ChaoXing;
import cn.yecaiyu.entity.ChxAccount;
import cn.yecaiyu.entity.ChxCourse;
import cn.yecaiyu.entity.ChxResource;
import cn.yecaiyu.entity.ChxSection;
import cn.yecaiyu.utils.ChxUtil;
import cn.yecaiyu.utils.DueWebsocket;
import cn.yecaiyu.utils.HttpRequestUtil;
import cn.yecaiyu.websocket.WebSocket;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
public class ChxTask extends Thread {
    private WebSocket webSocket;
    private ChxAccount account;

    public ChxTask(WebSocket webSocket, ChxAccount account) {
        this.webSocket = webSocket;
        this.account = account;
    }

    @Override
    public void run() {
        int reLoginTry = 0;

        try {
            ChaoXing chaoXing = new ChaoXing();
            webSocket.sendMessage("开始获取所有章节");
            account = chaoXing.getSelectedCourseData(account);
            ChxCourse curCourse = account.getCurCourse();
            List<ChxSection> sections = curCourse.getSections();
            int sessionNum = sections.size();
            int sessionIndex = 0;
            while (sessionIndex < sessionNum && account.getIsRunning()) {
                ChxSection section = sections.get(sessionIndex);
                sessionIndex++;
                webSocket.sendMessage("---- 开始读取章节信息 ----");
                log.info("开始读取章节信息");
                JsonObject knowledge = null;
                try {
                    knowledge = chaoXing.getSection(account, section); //读取章节信息
                } catch (Exception e) {
                    if (reLoginTry < 2) {
                        webSocket.sendMessage("章节数据错误,可能是课程存在验证码,正在尝试重新登录");
                        log.error("章节数据错误,可能是课程存在验证码,正在尝试重新登录");
                        chaoXing.reInitLogin(account);
                        sessionIndex--;
                        reLoginTry++;
                        continue;
                    } else {
                        webSocket.sendMessage("章节数据错误,可能是课程存在验证码,重新登录尝试无效");
                        log.error("章节数据错误,可能是课程存在验证码,重新登录尝试无效");
                        break;
                    }
                }
                reLoginTry = 0;
                JsonArray tabs = knowledge.get("card").getAsJsonObject()
                        .get("data").getAsJsonArray();
                for (int tabIndex = 0; tabIndex < tabs.size(); tabIndex++) {
                    webSocket.sendMessage("---- 开始读取标签信息 ----");
                    log.info("开始读取标签信息");
                    JsonObject attachmentsO = chaoXing.getAttachments(account, section.getNodeId(), tabIndex);
                    JsonArray attachments = attachmentsO.get("attachments").getAsJsonArray();
                    if (Objects.isNull(attachments) || attachments.isEmpty()) {
                        continue;
                    }
                    webSocket.sendMessage(String.format("--> 当前章节：%s - %s %n", section.getLabel(), section.getName()));
                    log.info(String.format("--> 当前章节：%s - %s %n", section.getLabel(), section.getName()));
                    for (JsonElement attachment : attachments) {
                        // 判断该视频是否为视频
                        JsonElement type = attachment.getAsJsonObject().get("type");

                        if (Objects.isNull(type) || !"video".equals(type.getAsString())) {
                            webSocket.sendMessage("****** 跳过非视频任务 ******");
                            log.info("****** 跳过非视频任务 ******");
                            continue;
                        }

                        String videoName = attachment.getAsJsonObject().get("property").getAsJsonObject().get("name").getAsString();
                        webSocket.sendMessage("----> 当前视频：" + videoName + "");
                        log.info("----> 当前视频：" + videoName + "");

                        // 判断当前视频是否已经看完了
                        JsonElement isPassedEl = attachment.getAsJsonObject().get("isPassed");

                        if (Objects.nonNull(isPassedEl) && isPassedEl.getAsBoolean()) {
                            showProgress(videoName, 1, 1);
                            Thread.sleep(1000);
                            continue;
                        }

                        String objectId = attachment.getAsJsonObject().get("objectId").getAsString();
                        String fid = attachmentsO.get("defaults").getAsJsonObject().get("fid").getAsString();
                        JsonObject videoInfo = chaoXing.getDToken(account, objectId, fid);

                        if (Objects.isNull(videoInfo)) {
                            continue;
                        }

                        String jobId = null;
                        if (Objects.nonNull(attachmentsO.get("jobid"))) {
                            jobId = attachmentsO.get("jobid").getAsString();
                        } else {
                            JsonElement jobIdEl = attachment.getAsJsonObject().get("jobid");
                            if (Objects.nonNull(jobIdEl)) {
                                jobId = jobIdEl.getAsString();
                            } else {
                                JsonObject property = attachment.getAsJsonObject().get("property").getAsJsonObject();
                                if (Objects.nonNull(property.get("jobid"))) {
                                    jobId = property.get("jobid").getAsString();
                                } else {
                                    if (Objects.nonNull(property.get("_jobid"))) {
                                        jobId = property.get("_jobid").getAsString();
                                    }
                                }
                            }
                        }

                        if (Objects.isNull(jobId)) {
                            webSocket.sendMessage("**** 未找到jobid，已跳过当前任务点 ****");
                            log.info("**** 未找到jobid，已跳过当前任务点 ****");
                            continue;
                        }
                        int duration = videoInfo.get("duration").getAsInt();
                        String dToken = videoInfo.get("dtoken").getAsString();
                        String otherInfo = attachment.getAsJsonObject().get("otherInfo").getAsString();

                        ChxResource resource = new ChxResource();
                        resource.setObjectId(objectId);
                        resource.setJobId(jobId);
                        resource.setDToken(dToken);
                        resource.setVideoName(videoName);
                        resource.setVideoDuration(duration);
                        resource.setOtherInfo(otherInfo);

                        // 播放视频
                        try {
                            passVideo(account, resource);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                            continue;
                        }

                        ChxUtil.pause(10, 13);
                    }
                }
            }
            webSocket.sendMessage("****** 任务结束 ******");
            log.info("****** 任务结束 ******");

            // 删除该用户的任务
            if (DueWebsocket.taskMap.containsKey(account.getUsername())) {
                DueWebsocket.taskMap.remove(account.getUsername());
            }
        } catch (Exception e) {
            // 出错了也删除该用户的任务
            if (DueWebsocket.taskMap.containsKey(account.getUsername())) {
                DueWebsocket.taskMap.remove(account.getUsername());
            }
            e.printStackTrace();
            Thread.currentThread().interrupt();
        }
    }

    private void passVideo(ChxAccount account, ChxResource resource) throws InterruptedException {
        int sec = 58;
        int playingTime = 0;
        webSocket.sendMessage("当前播放速率：1倍速");
        while (true) {
            if (sec >= 58) {
                sec = 0;
                JsonObject res = mainPassVideo(
                        account,
                        resource,
                        playingTime
                );
                boolean isPassed = res.get("isPassed").getAsBoolean();
                if (Objects.nonNull(isPassed) && isPassed) {
                    showProgress(resource.getVideoName(), resource.getVideoDuration(), resource.getVideoDuration());
                    break;
                } else if (Objects.nonNull(res.get("error"))) {
                    webSocket.sendMessage("出现错误");
                    log.error("出现错误");
                    continue;
                }
            }

            showProgress(resource.getVideoName(), playingTime, resource.getVideoDuration());
            playingTime += 1;
            sec += 1;
            Thread.sleep(1000);
        }
    }

    private JsonObject mainPassVideo(ChxAccount account, ChxResource resource, int playingTime) {
        ChxCourse curCourse = account.getCurCourse();

        String url = "https://mooc1-api.chaoxing.com/multimedia/log/a/" + curCourse.getCpi() + "/" + resource.getDToken();
        String enc = ChxUtil.getEnc(curCourse.getClazzId(), resource.getJobId(), resource.getObjectId(), playingTime, resource.getVideoDuration(), account.getUserid());
        Map<String, Object> params = new HashMap<>();
        params.put("otherInfo", resource.getOtherInfo());
        params.put("playingTime", String.valueOf(playingTime));
        params.put("duration", String.valueOf(resource.getVideoDuration()));
        params.put("jobid", resource.getJobId());
        params.put("clipTime", "0_" + resource.getVideoDuration());
        params.put("clazzId", String.valueOf(curCourse.getClazzId()));
        params.put("objectId", resource.getObjectId());
        params.put("userid", account.getUserid());
        params.put("isdrag", '0');
        params.put("enc", enc);
        params.put("rt", "0.9");
        params.put("dtype", "Video");
        params.put("view", "pc");
        params.put("_t", String.valueOf(System.currentTimeMillis()));
        StringBuilder newUrl = new StringBuilder(url + "?");
        boolean first = true;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (!first){
                newUrl.append("&");
            }
            newUrl.append(entry.getKey()).append("=").append(entry.getValue());
            first = false;
        }
        HttpResponse response = HttpRequestUtil.get(newUrl.toString(), account.getCookie(), null, null, null, null);
        System.out.println(response.body());
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private void showProgress(String name, float current, float total) {
        float percent = (current / total * 100);
        float remain = (total - current);
        if (current >= total && remain < 1) {
            webSocket.sendMessage("--> 当前任务 [ " + name + " ]已完成");
            log.info("--> 当前任务 [ " + name + " ]已完成");
        } else {
            webSocket.sendMessage("当前任务 [ " + name + " ] -> |" + String.format("%.2f", percent) + "%| ");
            log.info("当前任务 [ " + name + " ] -> |" + String.format("%.2f", percent) + "%| ");
        }
    }

}
