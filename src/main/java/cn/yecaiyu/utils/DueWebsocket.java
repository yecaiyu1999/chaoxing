package cn.yecaiyu.utils;

import cn.yecaiyu.ChaoXing;
import cn.yecaiyu.entity.ChxAccount;
import cn.yecaiyu.entity.ChxCourse;
import cn.yecaiyu.task.ChxTask;
import cn.yecaiyu.websocket.WebSocket;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class DueWebsocket {
    public static final ConcurrentHashMap<String, ChxAccount> accountMap = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<String, Thread> taskMap = new ConcurrentHashMap<>();

    public static void parseCommand(WebSocket webSocket, String command) {
        if (command.startsWith(">") && command.length() > 1) {
            String realCommand = command.split(">")[1];
            String[] args = realCommand.split(" ");
            String prefix = args[0];
            try {
                if ("set".equals(prefix)) {
                    // 存用户信息
                    saveUserInfo(webSocket, args[1], args[2]);
                } else if ("update".equals(prefix)) {
                    // 更新用户信息
                    updateUserInfo(webSocket, args[1], args[2]);
                } else if ("list".equals(prefix)) {
                    // 显示选课界面
                    listCourse(webSocket, args[1]);
                } else if ("select".equals(prefix)) {
                    // 选择课程
                    selectCourse(webSocket, args[1], args[2]);
                } else if ("run".equals(prefix)) {
                    // 开始挂机刷课
                    runTask(webSocket, args[1]);
                } else if ("stop".equals(prefix)) {
                    // 停止挂机刷课
                    stopTask(webSocket, args[1]);
                } else if ("del".equals(prefix)) {
                    // 删除用户数据
                    delUserInfo(webSocket, args[1]);
                } else {
                    webSocket.sendMessage("指令错误");
                }
            } catch (Exception e) {
                webSocket.sendMessage("指令错误");
                e.printStackTrace();
            }
        } else {
            webSocket.sendMessage("指令错误");
        }
    }

    private static void delUserInfo(WebSocket webSocket, String username) {
        if (!accountMap.containsKey(username)) {
            webSocket.sendMessage("该用户任务不存在");
        } else {
            if (taskMap.containsKey(username)) {
                // 先暂停任务
                ChxAccount chxAccount = accountMap.get(username);
                chxAccount.setIsRunning(false);
                // 删除任务，并关闭线程
                Thread chxTask = taskMap.get(username);
                if (Objects.nonNull(chxTask) && chxTask.isAlive()) {
                    chxTask.interrupt();
                }
                taskMap.remove(username);
            }
            // 暂停完后删除用户数据
            accountMap.remove(username);
            webSocket.sendMessage("用户数据删除成功~");
        }
    }

    private static void stopTask(WebSocket webSocket, String username) {
        if (!taskMap.containsKey(username)) {
            webSocket.sendMessage("该用户任务不存在，请先运行任务");
        } else {
            if (!accountMap.containsKey(username)) {
                webSocket.sendMessage("用户不存在,请先设置用户数据");
                return;
            }
            if (taskMap.containsKey(username)) {
                // 暂停任务
                webSocket.sendMessage("暂停任务....");
                ChxAccount chxAccount = accountMap.get(username);
                chxAccount.setIsRunning(false);
                // 删除任务，并关闭线程
                Thread chxTask = taskMap.get(username);
                if (Objects.nonNull(chxTask) && chxTask.isAlive()) {
                    chxTask.interrupt();
                }
                taskMap.remove(username);
            }
            webSocket.sendMessage("任务已暂停");
        }
    }

    private static void runTask(WebSocket webSocket, String username) {
        if (taskMap.containsKey(username)) {
            webSocket.sendMessage("该用户任务已经在运行中，请勿重复运行");
        } else {
            if (!accountMap.containsKey(username)) {
                webSocket.sendMessage("用户不存在,请先设置用户数据");
                return;
            }
            webSocket.sendMessage("开始任务....");
            ChxAccount chxAccount = accountMap.get(username);
            chxAccount.setIsRunning(true);
            ChxTask chxTask = new ChxTask(webSocket, chxAccount);
            chxTask.start();
            taskMap.put(username, chxTask);
            webSocket.sendMessage("任务已开启");
        }
    }

    private static void selectCourse(WebSocket webSocket, String username, String index) {
        if (!accountMap.containsKey(username)) {
            webSocket.sendMessage("用户不存在,请先设置用户数据");
        } else {
            ChxAccount chxAccount = accountMap.get(username);

            int i = Integer.parseInt(index);

            List<ChxCourse> allCourse = getCourses(webSocket, chxAccount);
            if (Objects.isNull(allCourse) || allCourse.size() == 0) {
                webSocket.sendMessage("没有课程可选~");
                return;
            }

            if (i > allCourse.size() || i < 1) {
                webSocket.sendMessage("课程序号输入错误~");
                return;
            }

            ChxCourse curCourse = allCourse.get(i - 1);
            chxAccount.setCurCourse(curCourse);

            accountMap.put(username, chxAccount);

            webSocket.sendMessage("已选择课程：" + curCourse.getCourseName());
        }
    }

    private static void listCourse(WebSocket webSocket, String username) {
        if (!accountMap.containsKey(username)) {
            webSocket.sendMessage("用户不存在,请先设置用户数据");
        } else {
            ChxAccount chxAccount = accountMap.get(username);

            // 获取课程信息
            List<ChxCourse> allCourse = getCourses(webSocket, chxAccount);

            StringBuilder sb = new StringBuilder();
            sb.append("<span>请请输入(>select 用户名 课程序号)命令来选择需要挂机的课程</span>");
            sb.append("<table border=\"1\">");
            sb.append("<tr><th>序号</th><th>课程名称</th><th>教师姓名</th></tr>");
            int count = 1;
            for (ChxCourse course : allCourse) {
                sb.append("<tr><td>").append(count++).append("</td><td>").append(course.getCourseName()).append("</td><td>").append(course.getTeacherName()).append("</td></tr>");
            }
            sb.append("</table>");

            accountMap.put(username, chxAccount);

            webSocket.sendMessage(sb.toString());
        }
    }

    private static List<ChxCourse> getCourses(WebSocket webSocket, ChxAccount chxAccount) {
        ChaoXing chaoXing = new ChaoXing();
        chxAccount = chaoXing.getAllCourse(chxAccount);
        if (Objects.isNull(chxAccount)) {
            webSocket.sendMessage("课程获取失败");
            return null;
        }
        return chxAccount.getAllCourse();
    }


    private static void updateUserInfo(WebSocket webSocket, String username, String password) {
        if (!accountMap.containsKey(username)) {
            webSocket.sendMessage("用户不存在,请先设置用户数据");
        } else {
            ChxAccount chxAccount = accountMap.get(username);
            chxAccount.setPassword(password);

            webSocket.sendMessage("验证是否能够登录超星，登录中.....");
            ChaoXing chaoXing = new ChaoXing();
            chxAccount = chaoXing.login(chxAccount.getUsername(), chxAccount.getPassword());
            if (Objects.isNull(chxAccount)) {
                webSocket.sendMessage("登录失败，用户或密码错误~");
                return;
            }
            accountMap.put(username, chxAccount);
            webSocket.sendMessage("验证登录成功~修改用户信息成功~");
        }
    }

    private static void saveUserInfo(WebSocket webSocket, String username, String password) {
        if (accountMap.containsKey(username)) {
            webSocket.sendMessage("用户已存在,无需重复添加");
        } else {
            ChxAccount chxAccount = new ChxAccount(username, password);
            webSocket.sendMessage("验证是否能够登录超星，登录中.....");
            ChaoXing chaoXing = new ChaoXing();
            chxAccount = chaoXing.login(chxAccount.getUsername(), chxAccount.getPassword());
            if (Objects.isNull(chxAccount)) {
                webSocket.sendMessage("登录失败，用户或密码错误~");
                return;
            }
            accountMap.put(username, chxAccount);
            webSocket.sendMessage("验证登录成功，设置用户信息成功");
        }
    }


}
