import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/**
 *      主方法
 *
 *      说明：
 *          1. 日志记录在每个类的 Method 的方法里
 *
 */
public class Main {

    // 设置日志记录
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        String secretStr;
        CloseableHttpClient session;
        BookingTicketMethodReturnResult bookingTicketMethodReturnResult;
        String logMsg;
        while (true) {
            // 列车鉴别码不需要用户设置，从查询车票的返回信息里获得
            secretStr = getSecretStr();
            long t1 = System.currentTimeMillis();
            // 登陆成功的session
            session = login();
            long t2 = System.currentTimeMillis();
            // 登陆超时30s 则重新查询列车信息
            if (t2 - t1 > 30000) {
                continue;
            }
            if (secretStr != null && session != null) {
                // 订票结果
                bookingTicketMethodReturnResult = bookingTicket(session, secretStr);
            } else {
                logMsg = "secretStr 或 session 为 null";
                logger.info(logMsg);
                SendMessage.sendNotify(logMsg, logMsg);
                continue;
            }
            if (bookingTicketMethodReturnResult != null) {
                // 发送结果
                sendNotify(bookingTicketMethodReturnResult);
                break;
            } else {
                logMsg = "bookingTicketMethodReturnResult 为 null";
                SendMessage.sendNotify(logMsg, logMsg);
            }
        }

    }
    /**
     *      获取列车 secret 码
     *      此方法阻塞线程直至查询到 secret 码
     *
     * @return  列车 secret 码
     */
    private static String getSecretStr(){
        Map<String, String> trainInfo;
        QueryTrainInfoReturnResult queryTrainInfoReturnResult;
        while (true){
            // 查询结果
            queryTrainInfoReturnResult = multiThreadQueryTicket();
            // 获得信息
            if (queryTrainInfoReturnResult.getStatusCode() == 0){
                trainInfo = queryTrainInfoReturnResult.getTrainInfo();
                break;
            }
            // 获得信息但是系统维护
            if (queryTrainInfoReturnResult.getStatusCode() == 2){
                logger.info("查询到票信息，但是现在系统维护......");
                // 如果在系统维护时间则进行等待
                while (true){
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        logger.info("线程睡眠过程被意外中断");
                    }
                    try {
                        if (!isMaintain()){
                            break;
                        }
                    } catch (Exception e) {
                        break;
                    }
                }
            }
        }
        // 列车鉴别码不需要用户设置，从查询车票的返回信息里获得
        return trainInfo.get("secretStr");
    }
    /**
     *      多线成执行请求
     *
     * @return                      QueryTrainInfoReturnResult
     *                              QueryTrainInfoReturnResult 对象的 statusCode 包含三种情况:
     *                              0 --> 查询到
     *                              1 --> 未查询到
     *                              2 --> 查询到但是系统维护
     *
     */
    private static QueryTrainInfoReturnResult multiThreadQueryTicket() {
        // 设置信息
        String bookingAfterTime = ConfigInfo.getBookingAfterTime();
        String bookingBeforeTime = ConfigInfo.getBookingBeforeTime();
        String trainDate = ConfigInfo.getTrainDate();
        String fromStation = ConfigInfo.getFromStation();
        String toStation = ConfigInfo.getToStation();
        String purposeCode = ConfigInfo.getPURPOSECODE();
        String userSetTrainName = ConfigInfo.getUserSetTrainName();
        // 创建对象获取查票结果
        QueryTrainInfoReturnResult queryTrainInfoReturnResult = new QueryTrainInfoReturnResult();
        // 线程池线程数
        int threadCount = 16;
        // 并发数
        int concurrentCount = threadCount * 2;
        // 手动创建一个线程池
        ThreadFactory namedThreadFactory = new ThreadFactoryBuilder()
                .setNameFormat("%d")
                .build();
        ExecutorService pool = new ThreadPoolExecutor(
                threadCount,
                200,
                30L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>(1024),
                namedThreadFactory,
                new ThreadPoolExecutor.AbortPolicy());
        try {
            // 设置并发
            List<Future<Map<String, String>>> futuresList = new ArrayList<Future<Map<String, String>>>();
            for (int i = 0; i < concurrentCount; i++) {
                Future<Map<String, String>> future = pool.submit(new MultiThreadingQueryTicket(bookingAfterTime,
                        bookingBeforeTime,
                        trainDate,
                        fromStation,
                        toStation,
                        purposeCode,
                        userSetTrainName));
                futuresList.add(future);
            }
            Iterator<Future<Map<String, String>>> iterator = futuresList.iterator();
            // 声明trainInfo
            Map<String, String> trainInfo = null;
            while (iterator.hasNext()) {
                Future<Map<String, String>> future = iterator.next();
                try {
                    trainInfo = future.get();
                    // 获得列车信息
                    if (trainInfo != null) {
                        // 系统维护时间
                        if (isMaintain()){
                            queryTrainInfoReturnResult.setStatus(false);
                            queryTrainInfoReturnResult.setStatusCode(2);
                            queryTrainInfoReturnResult.setMessage("获得符合条件的列车但是现在系统维护");
                            queryTrainInfoReturnResult.setTrainInfo(null);
                            return queryTrainInfoReturnResult;
                        }
                        // 不在系统维护时间
                        pool.shutdownNow();
                        queryTrainInfoReturnResult.setStatus(true);
                        queryTrainInfoReturnResult.setStatusCode(0);
                        queryTrainInfoReturnResult.setMessage("获得符合条件的列车");
                        queryTrainInfoReturnResult.setTrainInfo(trainInfo);
                        return queryTrainInfoReturnResult;
                    }
                } catch (InterruptedException e) {
                    logger.info(Thread.currentThread().getName() + "线程池中断异常");
                } catch (ExecutionException e) {
                    logger.info(Thread.currentThread().getName() + "线程池执行异常");
                }
            }
        }catch (Exception e){
            logger.info(String.format("请求超时，错误信息：%s", e.getMessage()));
        }
        // 关闭线程池
        pool.shutdown();
        // 能执行到这里说明没有符合条件的列车
        queryTrainInfoReturnResult.setStatus(false);
        queryTrainInfoReturnResult.setStatusCode(1);
        queryTrainInfoReturnResult.setMessage("未获得符合条件的列车");
        queryTrainInfoReturnResult.setTrainInfo(null);
        return queryTrainInfoReturnResult;
    }
    /**
     *      登陆方法
     *      这个方法会阻塞线程，直至登陆成功
     *
     * @return                  session
     */
    private static CloseableHttpClient login(){
        // 创建会话用于登陆订票
        CloseableHttpClient session = Tools.getSession(30000);
        // 创建登陆实例准备登陆
        String username12306 = ConfigInfo.getUsername12306();
        String password12306 = ConfigInfo.getPassword12306();
        Login login = new Login(session, username12306, password12306);
        LoginMethodReturnResutl loginMethodReturnResutl;

        try {
            loginMethodReturnResutl = login.loginMethod();
            // 不断尝试直至登陆成功
            while (!loginMethodReturnResutl.getStatus()){
                loginMethodReturnResutl = login.loginMethod();
            }
        } catch (Exception e) {
            logger.info("在登陆的过程中发送错误");
            return null;
        }
        return loginMethodReturnResutl.getSession();
    }
    /**
     *      订票方法, 只关心订票逻辑与返回处理结果
     *
     * @param session       session
     * @param secretStr     secretStr
     * @return              BookingTicketMethodReturnResult
     */
    private static BookingTicketMethodReturnResult bookingTicket(CloseableHttpClient session, String secretStr){
        String trainDate = ConfigInfo.getTrainDate();
        String backTrainDate = getCurrentDate();
        String purposeCode = ConfigInfo.getPURPOSECODE();
        String fromStation = ConfigInfo.getFromStation();
        String toStation = ConfigInfo.getToStation();
        String passengerName = ConfigInfo.getPassengerName();
        String documentType = ConfigInfo.getDocumentType();
        String documentNumber = ConfigInfo.getDocumentNumber();
        String mobile = ConfigInfo.getMOBILE();
        // seatTypeArr 用户设置的时候为 一等座，二等座，但是提交的时候需要转换成 O，M 这种形式
        String[] seatTypeArr = ConvertMap.convSeatNameToNumber(ConfigInfo.getSeatType());
        String expectSeatNumber = ConfigInfo.getExpectSeatNumber();

        BookingTicket bookingTicket = new BookingTicket(session);
        BookingTicketMethodReturnResult bookingTicketMethodReturnResult;
        bookingTicketMethodReturnResult = bookingTicket.bookingTicketMethod(secretStr,
                                                                            trainDate,
                                                                            backTrainDate,
                                                                            purposeCode,
                                                                            fromStation,
                                                                            toStation,
                                                                            passengerName,
                                                                            documentType,
                                                                            documentNumber,
                                                                            mobile,
                                                                            seatTypeArr,
                                                                            expectSeatNumber);
        return bookingTicketMethodReturnResult;
    }
    /**
     *      发送通知
     *      从 bookingTicketMethodReturnResult 里提取订票结果
     *      bookingTicketMethodReturnResult.status:
     *          true --> 发送成功通知, 通知方式从配置类读取
     *          false -> 发送失败通知, 通知方式从配置类读取
     *
     * @param bookingTicketMethodReturnResult bookingTicketMethodReturnResult
     */
    private static void sendNotify(BookingTicketMethodReturnResult bookingTicketMethodReturnResult){
        // 邮件, 语音, 短信设置
        String trainDate = ConfigInfo.getTrainDate();
        String fromStation = ConfigInfo.getFromStation();
        String toStation = ConfigInfo.getToStation();
        String soundURL = ConfigInfo.getSoundXmlUrl();
        String smsText = ConfigInfo.getSmsText();
        String subjectBookingFalse = "刷到票信息了，但是预定失败，快去看看。";
        String contentBookingFalse = "刷到票信息了，但是预定失败，快去看看。";
        String smsTextFalse = "刷到票信息了，但是预定失败，快去看看。";
        String subject = String.format("12306抢票结果通知 %s %s --> %s", trainDate, fromStation, toStation);
        String content = bookingTicketMethodReturnResult.getBookingTicketResult();
        boolean sendNotifyStatus = false;
        // 发送通知
        if (!bookingTicketMethodReturnResult.getStatus()){
            // 订票失败
            sendNotifyStatus = SendMessage.sendNotify(subjectBookingFalse, contentBookingFalse, smsTextFalse, soundURL);
        }else{
            // 订票成功
            sendNotifyStatus = SendMessage.sendNotify(subject, content, smsText, soundURL);
        }
        if (sendNotifyStatus){
            logger.info("发送通知成功");

        }else{
            logger.info("发送通知失败");
        }
    }
    /**
     *      获取当前日期 格式: 2019-01-30
     *
     * @return  当前日期
     */
    private static String getCurrentDate(){
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
        Date now = new Date();
        return simpleDateFormat.format(now);
    }
    /**
     *      判断系统是否维护
     *      因为当地时间为意大利时间，所以设置维护时间为 16：00 - 23：00
     *
     * @return
     * @throws Exception
     */
    private static Boolean isMaintain() throws Exception{
        SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm");
        Date date = dateFormat.parse(dateFormat.format(new Date(System.currentTimeMillis())));
        Date afterDate = dateFormat.parse("16:00");
        Date beforeDate = dateFormat.parse("23:00");
        if (afterDate.getTime() <= date.getTime() && date.getTime() <= beforeDate.getTime()){
            // System.out.println("系统维护时间");
            return true;

        }else{
            // System.out.println("正常运行......");
            return false;
        }
    }
}

/**
 *      多线程执行查票请求 Callable
 */
class MultiThreadingQueryTicket implements Callable<Map<String, String>> {


    private String bookingAfterTime;
    private String bookingBeforeTime;
    private String trainDate;
    private String fromStation;
    private String toStation;
    private String purposeCode;
    private String userSetTrainName;

    MultiThreadingQueryTicket(String bookingAfterTime,
                              String bookingBeforeTime,
                              String trainDate,
                              String fromStation,
                              String toStation,
                              String purposeCode,
                              String userSetTrainName) {
        this.bookingAfterTime = bookingAfterTime;
        this.bookingBeforeTime = bookingBeforeTime;
        this.trainDate = trainDate;
        this.fromStation = fromStation;
        this.toStation = toStation;
        this.purposeCode = purposeCode;
        this.userSetTrainName = userSetTrainName;
    }

    @Override
    public Map<String, String> call() throws Exception {
        return QueryTicketInfo.trainInfoMethod(bookingAfterTime,
                bookingBeforeTime,
                trainDate,
                fromStation,
                toStation,
                purposeCode,
                userSetTrainName);
    }
}

/**
 *  返回查票的结果
 *  status, statusCode, message, trainInfo(Map<String,String>)
 *  statusCode:
 *              0 --> 获得符合条件的列车
 *              1 --> 未获得符合条件的列车
 *              2 --> 获得符合条件的列车但是现在系统维护
 */
class QueryTrainInfoReturnResult{

    public Boolean getStatus() {
        return status;
    }

    public void setStatus(Boolean status) {
        this.status = status;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(Integer statusCode) {
        this.statusCode = statusCode;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Map<String, String> getTrainInfo() {
        return trainInfo;
    }

    public void setTrainInfo(Map<String, String> trainInfo) {
        this.trainInfo = trainInfo;
    }

    private Boolean status;
    private Integer statusCode;
    private String  message;
    private Map<String, String> trainInfo;

}