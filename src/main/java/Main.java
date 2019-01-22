import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 *      主方法
 *
 *      说明：
 *          1. 日志记录在每个类的 Method 的方法里
 *
 */
public class Main {

    public void setSession(CloseableHttpClient session) {
        this.session = session;
    }
    public CloseableHttpClient getSession() {
        return this.session;
    }
    private CloseableHttpClient session;

    // 设置日志记录
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception{
        /*
                需要用户提供的信息
        格式：
             username12306      --> xxx         12306网站用户名
             password12306      --> xxx         12306网站密码
             bookingAfterTime   --> 09:00       选择列车的时间范围
             bookingBeforeTime  --> 12:00
             trainDate          --> 2019-01-31  列车日期
             fromStation        --> 西安         出发站
             toStation          --> 运城         到达站
             purposeCode        --> ADULT       这个默认成人票
             userSetTrainName   --> D2568       列车号，如果未空则取符合条件的最早一班
             passengerName      --> xxx         乘客姓名
             documentType       --> 1           证件类型，1 是身份证， B 是护照
             documentNumber     --> 123456      证件号
             mobile             --> 11111111111 手机号
             seatType           --> 二等座       座位类型
             expectSeatNumber   --> A           期望的座位号，A,B,C,E,F  A 和 F 靠窗，这个不一定能选上
         */
        String username12306 = "";
        String password12306 = "";
        String bookingAfterTime = "09:00";
        String bookingBeforeTime = "12:00";
        String trainDate = "2019-01-31";
        String fromStation = "西安";
        String toStation = "运城";
        String purposeCode = "ADULT";
        String userSetTrainName = "";
        String backTrainDate = getCurrentDate();
        String passengerName = "";
        String documentType = "";
        String documentNumber = "";
        String mobile = "";
        String seatType = ConvertMap.seatNameToNumber("二等座");
        String expectSeatNumber = "";
        /*
            接收通知设置

        格式:
            host            -> smtp.gmail.com
            port            -> 465             SSl安全连接端口号，普通的可能是25
            usernameEmail   -> xxxx
            passwordEmail   -> xxxx
            senderEmail     -> xxxx@xxx.com
         */
        String host = "";
        String port = "465";
        String usernameEmail = "";
        String passwordEmail = "";
        String senderEmail = "";
        String receiverEmail = "";
        /*
            语音和短信通知接口设置

                1. 使用Twilio的通知服务，accountSid，authToken 从自己的账户中获得
                2. 注意电话前要加国家号，例如中国为：+86 以及注意是否有发送到目标号码的权限（从Twilio网站里设置）
                   号码示例：
                            +8612345678912
                3. configPath 是一个url，指向一个xml文件，具体内容参见Twilio开发者文档
                   configPath示例：
                            http://www.xxx.com/xxx.xml
        */
        String accountSid="";
        String authToken = "";
        String from = "";
        String[] tos = {""};
        String configPath = "";
        String smsText = "恭喜您！抢到票了，请登录12306进行支付。";

        // 获取列车信息（单线程）
        Map<String, String> trainInfo = null;
        while (true){
            try{
                trainInfo = QueryTicketInfo.trainInfoMethod(bookingAfterTime,
                        bookingBeforeTime,
                        trainDate,
                        fromStation,
                        toStation,
                        purposeCode,
                        userSetTrainName);
            }catch (Exception e){
                logger.info(String.format("请求超时，错误信息：%s", e.getMessage()));
            }

            if (trainInfo != null){
                break;
            }
        }


        // 列车鉴别码不需要用户设置，从查询车票的返回信息里获得
        String secretStr = trainInfo.get("secretStr");

        // 创建会话
        CloseableHttpClient session = Tools.getSession(30000);

        // 创建登陆实例准备登陆
        Login login = new Login(session, username12306, password12306);
        LoginMethodReturnResutl loginMethodReturnResutl;
        loginMethodReturnResutl = login.loginMethod();
        while (!loginMethodReturnResutl.getStatus()){
            loginMethodReturnResutl = login.loginMethod();
        }
        session = loginMethodReturnResutl.getSession();

        // 创建订票实例准备订票
        BookingTicket bookingTicket = new BookingTicket(session);
        BookingTicketMethodReturnResult bookingTicketMethodReturnResult;
        bookingTicketMethodReturnResult = bookingTicket.bookingTicketMethod(secretStr,
                                                                            trainDate,
                                                                            getCurrentDate(),
                                                                            purposeCode,
                                                                            fromStation,
                                                                            toStation,
                                                                            passengerName,
                                                                            documentType,
                                                                            documentNumber,
                                                                            mobile,
                                                                            seatType,
                                                                            expectSeatNumber);
        if (!bookingTicketMethodReturnResult.getStatus()){
            SendMessage.sendEmailText(host,
                                      port,
                                      usernameEmail,
                                      passwordEmail,
                                      senderEmail,
                                      receiverEmail,
                                      "刷到票信息了，但是预定失败，快去看看。",
                                      "刷到票信息了，但是预定失败，快去看看。",
                                      true);
            return;
        }
        String subject = String.format("12306抢票结果通知 %s %s --> %s", trainDate, fromStation, toStation);
        String content = bookingTicketMethodReturnResult.getBookingTicketResult();

        // 发送通知
        boolean sendEmailStatus = false;
        boolean sendPhoneStatus = false;
        boolean sendSmsStatus   = false;
        sendEmailStatus = SendMessage.sendEmailText(host, port, usernameEmail, passwordEmail, senderEmail, receiverEmail, subject, content, true);
        sendPhoneStatus = SendMessage.sendPhoneVoiceNotifyFromTwilio(accountSid, authToken, from, tos, configPath);
        sendSmsStatus   = SendMessage.sendSMSNotifyFromTwilio(accountSid, authToken, from, tos, smsText);
        if (sendEmailStatus && sendPhoneStatus && sendSmsStatus){
            logger.info("发送通知成功");
        }else{
            logger.info("发送通知失败");
        }

    }

    /**
     *      获取当前日期
     *
     * @return  当前日期
     */
    private static String getCurrentDate(){
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
        Date now = new Date();
        return simpleDateFormat.format(now);
    }
}


