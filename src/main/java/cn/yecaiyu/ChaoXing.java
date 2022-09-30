package cn.yecaiyu;

import cn.hutool.crypto.Mode;
import cn.hutool.crypto.Padding;
import cn.hutool.crypto.symmetric.DES;
import cn.hutool.http.HttpResponse;
import cn.yecaiyu.comparator.ChxSectionComparator;
import cn.yecaiyu.entity.ChxAccount;
import cn.yecaiyu.entity.ChxCourse;
import cn.yecaiyu.entity.ChxResource;
import cn.yecaiyu.entity.ChxSection;
import cn.yecaiyu.utils.ChxUtil;
import cn.yecaiyu.utils.HttpRequestUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.net.HttpCookie;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * 根据activeId获取主题信息
 * https://mobilelearn.chaoxing.com/v2/apis/discuss/getTopicDiscussInfo?activeId=1000022160108
 * activeId=1000022160108
 * <p>
 * https://groupweb.chaoxing.com/course/topicDiscuss/f6738c31278944d0af44476518b10aba_topicDiscuss/getTopic
 * <p>
 * <p>
 * https://groupweb.chaoxing.com/course/topicDiscuss/f6738c31278944d0af44476518b10aba_topicDiscuss/getTopic
 */

@Data
@NoArgsConstructor
public class ChaoXing {
    private final Logger logger = Logger.getLogger(this.getClass().getName());

    public void reInitLogin(ChxAccount account) {
        login(account.getUsername(), account.getPassword());
    }

    /**
     * 登录
     */
    public ChxAccount login(String username, String password) {
        String url = "https://passport2.chaoxing.com/fanyalogin";
        String host = "passport2.chaoxing.com";
        String origin = "https://passport2.chaoxing.com";
        String referer = "https://passport2.chaoxing.com/login?fid=&newversion=true&refer=http://i.mooc.chaoxing.com";

        Map<String, Object> params = new HashMap<>();
        params.put("fid", "-1");
        params.put("uname", username);
        DES des = new DES(Mode.ECB, Padding.PKCS5Padding, "u2oh6Vu^HWe40fj".getBytes());
        params.put("password", des.encryptHex(password));
        params.put("t", "true");
        params.put("refer", "http://i.mooc.chaoxing.com");
        logger.info("发送登录数据");

        HttpResponse response = HttpRequestUtil.post(url, null, host, referer, origin, params);
        JsonElement element = JsonParser.parseString(response.body());
        JsonObject object = element.getAsJsonObject();
        boolean status = object.get("status").getAsBoolean();

        logger.info("收到返回数据");
        if (status) {
            ChxAccount account = new ChxAccount(username, password);
            List<HttpCookie> cookies = response.getCookies();
            StringBuilder retCookies = new StringBuilder();
            for (HttpCookie cookie : cookies) {
                // 获取uid
                if ("_uid".equals(cookie.getName())) {
                    account.setUserid(cookie.getValue());
                }
                // 拼接cookie
                retCookies.append(cookie.getName()).append("=").append(cookie.getValue()).append(";");
            }
            account.setCookie(retCookies.toString());
            logger.info("登录成功");
            return account;
        }
        logger.warning("登录失败");
        return null;
    }

    /**
     * 获取所有课程信息
     */
    public ChxAccount getAllCourse(ChxAccount account) {
        String url = "https://mooc1-api.chaoxing.com/mycourse/backclazzdata?view=json&mcode=";
        HttpResponse response = HttpRequestUtil.get(url, account.getCookie(), null, null, null, null);

        JsonElement element = JsonParser.parseString(response.body());
        JsonObject object = element.getAsJsonObject();
        int result = object.get("result").getAsInt();

        if (result == 1) {
            // 所有课程信息
            List<ChxCourse> allCourse = new ArrayList<>();

            logger.info("获取课程信息成功");
            JsonArray channelList = object.get("channelList").getAsJsonArray();
            int channelLen = channelList.size();
            for (JsonElement channelEl : channelList) {
                ChxCourse course = new ChxCourse();
                JsonObject channel = channelEl.getAsJsonObject();
                JsonObject curCourse = channel.get("content").getAsJsonObject()
                        .get("course").getAsJsonObject();

                String courseId = curCourse.get("data").getAsJsonArray()
                        .get(0).getAsJsonObject()
                        .get("id").getAsString();

                String courseName = curCourse.get("data").getAsJsonArray()
                        .get(0).getAsJsonObject()
                        .get("name").getAsString();

                String teacherName = curCourse.get("data").getAsJsonArray()
                        .get(0).getAsJsonObject()
                        .get("teacherfactor").getAsString();

                String cpi = channel.get("cpi").getAsString();
                String clazzId = channel.get("content").getAsJsonObject().get("id").getAsString();

                course.setCourseId(courseId);
                course.setCourseName(courseName);
                course.setTeacherName(teacherName);
                course.setCpi(cpi);
                course.setClazzId(clazzId);
                allCourse.add(course);
            }
            account.setAllCourse(allCourse);
            return account;
        }
        logger.warning("获取课程信息失败");
        return null;
    }

    /**
     * 选择课程
     */
    public ChxAccount selectCourse(ChxAccount account) {
        logger.info("开始选课");
        String teacherNameFormat = "%-5.5s";
        String countFormat = "\t%-5.5s";
        String courseFormat = "\t%-30.30s %n";
        String format = teacherNameFormat.concat(countFormat).concat(courseFormat);
        System.out.format(format, "序号", "教师姓名", "课程名称");
        int count = 1;

        List<ChxCourse> allCourse = account.getAllCourse();

        for (ChxCourse course : account.getAllCourse()) {
            System.out.format(format, count++, course.getTeacherName(), course.getCourseName());
        }

        Scanner scanner = new Scanner(System.in);
        System.out.print("请输入你要选择的课程序号： ");
        int index = scanner.nextInt();
        if (index > 0 && index <= allCourse.size()) {
            ChxCourse curCourse = allCourse.get(index - 1);
            logger.info("已选课程: " + curCourse.getCourseName());
            account.setCurCourse(curCourse);
            return account;
        }
        logger.info("出现错误!");
        return null;
    }

    /**
     * 获取已选课程的所有章节信息
     */
    public ChxAccount getSelectedCourseData(ChxAccount account) {
        String url = "https://mooc1-api.chaoxing.com/gas/clazz";
        Map<String, Object> params = new HashMap<>();
        params.put("id", account.getCurCourse().getClazzId());
        params.put("fields", "id,bbsid,classscore,isstart,allowdownload,chatid,name,state,isthirdaq,isfiled,information,discuss,visiblescore,begindate,coursesetting.fields(id,courseid,hiddencoursecover,hiddenwrongset,coursefacecheck),course.fields(id,name,infocontent,objectid,app,bulletformat,mappingcourseid,imageurl,teacherfactor,knowledge.fields(id,name,indexOrder,parentnodeid,status,layer,label,begintime,endtime,attachment.fields(id,type,objectid,extension).type(video)))");
        params.put("view", "json");
        HttpResponse response = HttpRequestUtil.get(url, account.getCookie(), null, null, null, params);

        JsonElement element = JsonParser.parseString(response.body());
        JsonObject object = element.getAsJsonObject();

        JsonArray knowledgeArray = object
                .get("data").getAsJsonArray()
                .get(0).getAsJsonObject()
                .get("course").getAsJsonObject()
                .get("data").getAsJsonArray()
                .get(0).getAsJsonObject()
                .get("knowledge").getAsJsonObject()
                .get("data").getAsJsonArray();

        ArrayList<ChxSection> sections = new ArrayList<>();

        for (JsonElement jsonElement : knowledgeArray) {
            JsonObject knowledgeJson = jsonElement.getAsJsonObject();
            String label = knowledgeJson.get("label").getAsString();
            String name = knowledgeJson.get("name").getAsString();
            String nodeId = String.valueOf(knowledgeJson.get("id").getAsInt());
            sections.add(new ChxSection(name, label, nodeId));
        }
        sections.sort(new ChxSectionComparator());

        account.getCurCourse().setSections(sections);
        return account;
    }


    /**
     * 获取章节信息
     */
    public JsonObject getSection(ChxAccount account, ChxSection section) {
        ChxCourse curCourse = account.getCurCourse();
        String[] enc = ChxUtil.getEncTime();

        String url = "https://mooc1-api.chaoxing.com/gas/knowledge";
        Map<String, Object> params = new HashMap<>();
        params.put("id", section.getNodeId());
        params.put("courseid", curCourse.getCourseId());
        params.put("fields", "id,parentnodeid,indexorder,label,layer,name,begintime,createtime,lastmodifytime,status,jobUnfinishedCount,clickcount,openlock,card.fields(id,knowledgeid,title,knowledgeTitile,description,cardorder).contentcard(all)");
        params.put("token", "4faa8662c59590c6f43ae9fe5b002b42");
        params.put("_time", enc[0]);
        params.put("inf_enc", enc[1]);
        params.put("view", "json");
        HttpResponse response = HttpRequestUtil.get(url, account.getCookie(), null, null, null, params);

        JsonElement element = JsonParser.parseString(response.body());
        JsonObject object = element.getAsJsonObject();
        JsonElement data = object.get("data");
        JsonElement error = object.get("error");
        if (Objects.isNull(data) || Objects.nonNull(error)) {
            throw new RuntimeException("获取章节信息失败");
        }
        return data.getAsJsonArray().get(0).getAsJsonObject();
    }


    public JsonObject getAttachments(ChxAccount account, String knowledgeId, Integer num) {
        String url = "https://mooc1-api.chaoxing.com/knowledge/cards";
        Map<String, Object> params = new HashMap<>();
        params.put("clazzid", account.getCurCourse().getClazzId());
        params.put("courseid", account.getCurCourse().getCourseId());
        params.put("knowledgeid", knowledgeId);
        params.put("num", num);
        params.put("isPhone", 1);
        params.put("control", Boolean.TRUE);
        HttpResponse response = HttpRequestUtil.get(url, account.getCookie(), null, null, null, params);
        String content = response.body();

        // 正则匹配
        String regx = "window.AttachmentSetting *=(.*?);\\n";
        Matcher m = Pattern.compile(regx).matcher(content);

        if (m.find()) {
            return JsonParser.parseString(m.group(1)).getAsJsonObject();
        }

        return null;
    }


    public JsonObject getDToken(ChxAccount account, String objectId, String fid) {
        String url = "https://mooc1-api.chaoxing.com/ananas/status/" + objectId;
        Map<String, Object> params = new HashMap<>();
        params.put("k", fid);
        params.put("flag", "normal");
        params.put("_dc", new Date().getTime());
        HttpResponse response = HttpRequestUtil.get(url, account.getCookie(), null, null, null, params);
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }


    public void passVideo(ChxAccount account, ChxResource resource) {
        int sec = 58;
        int playingTime = 0;
        System.out.println("当前播放速率：1倍速");
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
                    ChxUtil.showProgress(resource.getVideoName(), resource.getVideoDuration(), resource.getVideoDuration());
                    break;
                } else if (Objects.nonNull(res.get("error"))) {
                    System.out.println("出现错误");
                    continue;
                }
            }

            ChxUtil.showProgress(resource.getVideoName(), playingTime, resource.getVideoDuration());
            playingTime += 1;
            sec += 1;
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
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


    public void doWork(ChxAccount account) {
        int reLoginTry = 0;
        logger.info("登陆中");
        account = login(account.getUsername(), account.getPassword());
        if (Objects.isNull(account)) {
            return;
        }
        logger.info("正在读取所有课程");
        account = getAllCourse(account);
        if (Objects.isNull(account)) {
            return;
        }
        logger.info("进行选课");
        account = selectCourse(account);
        if (Objects.isNull(account)) {
            return;
        }
        logger.info("开始获取所有章节");
        account = getSelectedCourseData(account);
        ChxCourse curCourse = account.getCurCourse();
        List<ChxSection> sections = curCourse.getSections();
        int sessionNum = sections.size();
        int sessionIndex = 0;
        while (sessionIndex < sessionNum) {
            ChxSection section = sections.get(sessionIndex);
            sessionIndex++;
            System.out.println("---- 开始读取章节信息 ----");
            JsonObject knowledge = null;
            try {
                knowledge = getSection(account, section); // 读取章节信息
            } catch (Exception e) {
                if (reLoginTry < 2) {
                    logger.warning("章节数据错误,可能是课程存在验证码,正在尝试重新登录");
                    reInitLogin(account);
                    sessionIndex--;
                    reLoginTry++;
                    continue;
                } else {
                    logger.warning("章节数据错误,可能是课程存在验证码,重新登录尝试无效");
                    break;
                }
            }
            reLoginTry = 0;

            JsonArray tabs = knowledge.get("card").getAsJsonObject()
                    .get("data").getAsJsonArray();
            for (int tabIndex = 0; tabIndex < tabs.size(); tabIndex++) {
                System.out.println("---- 开始读取标签信息 ----");
                JsonObject attachmentsO = getAttachments(account, section.getNodeId(), tabIndex);
                JsonArray attachments = attachmentsO.get("attachments").getAsJsonArray();
                if (Objects.isNull(attachments) || attachments.isEmpty()) {
                    continue;
                }
                System.out.printf("--> 当前章节：%s - %s %n", section.getLabel(), section.getName());
                for (JsonElement attachment : attachments) {
                    // 判断该视频是否为视频
                    JsonElement type = attachment.getAsJsonObject().get("type");

                    if (Objects.isNull(type) || !"video".equals(type.getAsString())) {
                        System.out.println("****** 跳过非视频任务 ******");
                        continue;
                    }

                    String videoName = attachment.getAsJsonObject().get("property").getAsJsonObject().get("name").getAsString();
                    System.out.println("----> 当前视频：" + videoName + "");
                    // 判断当前视频是否已经看完了
                    JsonElement isPassedEl = attachment.getAsJsonObject().get("isPassed");
                    if (Objects.nonNull(isPassedEl)) {
                        boolean isPassed = isPassedEl.getAsBoolean();
                        if (isPassed) {
                            ChxUtil.showProgress(videoName, 1, 1);
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            continue;
                        }
                    }

                    String objectId = attachment.getAsJsonObject().get("objectId").getAsString();
                    String fid = attachmentsO.get("defaults").getAsJsonObject().get("fid").getAsString();
                    JsonObject videoInfo = getDToken(account, objectId, fid);
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
                        System.out.println("**** 未找到jobid，已跳过当前任务点 ****");
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
                    passVideo(account, resource);

                    ChxUtil.pause(10, 13);

                }
            }
        }
        System.out.println("任务已结束~");
    }

}
