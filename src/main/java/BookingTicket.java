import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 *      这个类主要用于订票, 包含了一些订票的方法
 *
 *      订票请求流程:
 *      1.  POST    https://kyfw.12306.cn/otn/leftTicket/submitOrderRequest
 *      2.  POST    https://kyfw.12306.cn/otn/confirmPassenger/initDc
 *      3.  POST    https://kyfw.12306.cn/otn/confirmPassenger/getPassengerDTOs
 *      4.  POST    https://kyfw.12306.cn/otn/confirmPassenger/checkOrderInfo
 *      5.  POST    https://kyfw.12306.cn/otn/confirmPassenger/getQueueCount
 *      6.  POST    https://kyfw.12306.cn/otn/confirmPassenger/confirmSingleForQueue
 *      7.  GET     https://kyfw.12306.cn/otn/confirmPassenger/queryOrderWaitTime?random=1547380218487&tourFlag=dc&_json_att=&REPEAT_SUBMIT_TOKEN=c85c5ed09fd0e0804ca07a953b4a756c
 *      8.  POST    https://kyfw.12306.cn/otn/confirmPassenger/resultOrderForDcQueue
 *      9.  POST    https://kyfw.12306.cn/otn//payOrder/init?random=1547380219920
 *
 */
public class BookingTicket {

    private CloseableHttpClient session;
    private InitHtmlInfo initHtmlInfo;
    private BookingTicketResultInfo bookingTicketResultInfo;
    private String repeatSubmitToken;
    private List<String> optionalSeatType;

    // 设置日志记录
    private static final Logger logger = LoggerFactory.getLogger(BookingTicket.class);

    // 构造器
    public BookingTicket(CloseableHttpClient session){
        this.session = session;
    }

    /**
     *      获取 trainDate GMT 字符串
     *
     * @param trainDate     String 时间戳, 到毫秒
     * @return              TrainDatePost请求字符串
     * @throws Exception    异常处理
     */
    private String getTrainDateGMT(String trainDate) throws Exception{
        // GMT 时间
        Date timeValue = new Date(Long.parseLong(trainDate));
        SimpleDateFormat gmtTime = new SimpleDateFormat("HH", Locale.US);
        gmtTime.setTimeZone(TimeZone.getTimeZone("GMT"));
        long gmtTimeHour = Long.parseLong(gmtTime.format(timeValue));
        // 获取当前时间
        SimpleDateFormat currentTime = new SimpleDateFormat("HH", Locale.US);
        long currentTimeHour = Long.parseLong(currentTime.format(timeValue));
        // 差值
        long diffrence = currentTimeHour - gmtTimeHour;
        // 构造格式化时间字符串
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("EEE/MMM/d/yyyy/HH:mm:ss", Locale.US);
        String[] trainDateList = simpleDateFormat.format(new Date(Long.parseLong(trainDate))).split("/");
        String trainDateStr = "";
        for (int i = 0; i<trainDateList.length; i++){
            if (i==trainDateList.length-1){
                trainDateStr += Tools.encodeURL(trainDateList[i]);
                break;
            }
            trainDateStr += trainDateList[i] + "+";
        }
        // 构造GMT时区字符串
        String gmtStr="";
        if (diffrence>=0){
            gmtStr = "GMT+" + String.format("%04d",diffrence*100);
        }else{
            gmtStr = "GMT-" + String.format("%04d", Math.abs(diffrence*100));
        }
        gmtStr = trainDateStr + "+" + Tools.encodeURL(gmtStr);
        return gmtStr;
    }

    /**
     *      合并Map到Post请求接受的字符串
     *      例如:{"a":"1", "b":"2"} -> a=1&b=2
     *
     * @param dataMap   请求表单
     * @return          字符串
     */
    private static String mergerDataToString(Map<String, String> dataMap){
        StringBuilder dataStr = new StringBuilder();
        for (Map.Entry<String, String> entry: dataMap.entrySet()){
            // dataStr += entry.getKey() + "=" + entry.getValue() + "&";
            dataStr.append(entry.getKey());
            dataStr.append("=");
            dataStr.append(entry.getValue());
            dataStr.append("&");
        }
        return dataStr.substring(0, dataStr.length()-1).toString();
    }

    /**
     *      提交 submitOrderRequest 请求
     *
     * @param secretStr             列车鉴别码
     * @param trainDate             列车发车时间
     * @param backTrainDate         列车返程时间
     * @param purposeCode           乘客类型码
     * @param queryFromStationName  始发站
     * @param queryToStationName    到达站
     * @return                      SubmitOrderRequestReturnResult 对象
     *                              -->
     *                              status，message
     *                              布尔    字符串
     */
    public SubmitOrderRequestReturnResult submitOrderRequest(String secretStr,
                                      String trainDate,
                                      String backTrainDate,
                                      String purposeCode,
                                      String queryFromStationName,
                                      String queryToStationName)throws Exception{
        String submitOrderRequestURL = "https://kyfw.12306.cn/otn/leftTicket/submitOrderRequest";
        HttpPost submitOrderRequest = Tools.setRequestHeader(new HttpPost(submitOrderRequestURL), true, false, true);
        submitOrderRequest.addHeader("Referer", "https://kyfw.12306.cn/otn/leftTicket/init");
        submitOrderRequest.addHeader("Accept-Encoding", "gzip, deflate, br");

        // 创建提交表单
        Map<String, String> submitOrderRequestData = new HashMap<>();
        submitOrderRequestData.put("secretStr", Tools.decodeURL(secretStr));
        submitOrderRequestData.put("train_date", trainDate);
        submitOrderRequestData.put("back_train_date", backTrainDate);
        submitOrderRequestData.put("tour_flag", "dc");
        submitOrderRequestData.put("purpose_codes", purposeCode);
        submitOrderRequestData.put("query_from_station_name", queryFromStationName);
        submitOrderRequestData.put("query_to_station_name", queryToStationName);
        submitOrderRequestData.put("undefined", "");
        // 设置请求body
        submitOrderRequest.setEntity(Tools.doPostData(submitOrderRequestData));
        CloseableHttpResponse response = null;
        SubmitOrderRequestReturnResult submitOrderRequestReturnResult = new SubmitOrderRequestReturnResult();
        try{
            response = this.session.execute(submitOrderRequest);
            if(response.getStatusLine().getStatusCode()==200){
                String originalResult = EntityUtils.toString(response.getEntity(), "UTF-8");
                JSONObject submitOrderRequestJsonData = JSONObject.parseObject(originalResult);
                // 返回status为true
                if(submitOrderRequestJsonData.getBoolean("status")){
                    submitOrderRequestReturnResult.setStatus(true);
                    submitOrderRequestReturnResult.setMessage(submitOrderRequestJsonData.getString("messages"));
                    return submitOrderRequestReturnResult;
                }else{
                    submitOrderRequestReturnResult.setStatus(false);
                    submitOrderRequestReturnResult.setMessage(submitOrderRequestJsonData.getString("messages"));
                    return submitOrderRequestReturnResult;
                }
            }
        }
        finally {
            if (response!=null){
                response.close();
            }
        }
        submitOrderRequestReturnResult.setStatus(false);
        submitOrderRequestReturnResult.setMessage("请求 submitOrderRequest 失败");
        return submitOrderRequestReturnResult;
    }
    /**
     *      设置 ReportSubmitToken
     *
     * @param htmlText  网页源码, 从网页源码里提取ReportSubmitToken
     * @return          T --> true
     *                  F --> false
     */
    private boolean setReportSubmitToken(String htmlText){
        try {
            String[] scriptList = htmlText.split("\n");
            for (String line: scriptList){
                if(line.contains("globalRepeatSubmitToken")){
                    this.repeatSubmitToken = line.substring(line.indexOf("'")+1, line.length()-2);
                    return true;
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     *      设置 InitHtmlInfo 类，里面包含了订票需要用的信息
     *
     * @param htmlText  网页源码，之后会交给 getTicketInfoForPassengerForm 处理
     * @return          T --> true
     *                  F --> false
     */
    private boolean setInitHtmlInfo(String htmlText){
        String ticketInfoForPassengerForm = getTicketInfoForPassengerForm(htmlText);
        if(!ticketInfoForPassengerForm.isEmpty()){
            this.initHtmlInfo = new InitHtmlInfo(ticketInfoForPassengerForm);
            return true;
        }
        return false;
    }

    /**
     *      提取可以选择的座位, 设置 optionalSeatType
     *
     * @return  T -- true
     *          F -- false
     */
    private boolean setOptionalSeatType(){
        String[] trainSeatList = this.initHtmlInfo.getLeftDetails();
        List<String> seatList = new ArrayList<>();
        for (String line: trainSeatList){
            // 一等座(933.00元)有票
            String seatTypeName = line.split("\\(")[0].trim();
            String isHasTicket = line.split("\\)")[1].trim();
            // 有票
            if (!isHasTicket.contains("无")){
                seatList.add(ConvertMap.seatNameToNumber(seatTypeName));
            }
        }
        if (seatList.size() > 0){
            optionalSeatType = seatList;
            return true;
        }
        return false;
    }
    /**
     *      从 initDc.html 里 提取 ticketInfoForPassengerForm 字段
     *
     * @param htmlText  网页源码, 从网页源码里提取 ticketInfoForPassengerForm 字段
     * @return          T --> ticketInfoForPassengerForm 字段
     *                  F --> ""
     */
    private String getTicketInfoForPassengerForm(String htmlText){
        try {
            String[] scriptList = htmlText.split("\n");
            for (String line: scriptList){
                if(line.contains("var ticketInfoForPassengerForm")){
                    return line.substring(line.indexOf("{"), line.length()-1);
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }
    /**
     *      请求 initDc 页面
     *      这个页面会返回很多有用的信息，从这个页面里提取订票需要用到的信息
     *      设置属性：
     *                  reportSubmitToken   字符串
     *                  initHtmlInfo        类
     *
     * @return          T --> true
     *                  F --> false
     */
    public boolean initDc()throws Exception{
        String initDcURL = "https://kyfw.12306.cn/otn/confirmPassenger/initDc";
        HttpPost initDcRequest = Tools.setRequestHeader(new HttpPost(initDcURL), true, false, true);
        Map<String, String> initDcData = new HashMap<>();
        initDcData.put("_json_att", "");
        initDcRequest.setEntity(Tools.doPostData(initDcData));
        CloseableHttpResponse response=null;
        try{
            response = this.session.execute(initDcRequest);
            if(response.getStatusLine().getStatusCode()==200){
                String originalResutl = EntityUtils.toString(response.getEntity(), "UTF-8");
                // 设置 reportSubmitToken 和 initHtmlInfo
                boolean reportSubmitTokenStatus = setReportSubmitToken(originalResutl);
                boolean initHtmlInfoStatus = setInitHtmlInfo(originalResutl);
                // reportSubmitToken 和 initHtmlInfo 都设置成功
                if(reportSubmitTokenStatus && initHtmlInfoStatus){
                    return true;
                }
            }
        }
        finally {
            if (response!=null){
                response.close();
            }
        }
        return false;
    }

    /**
     *       请求 getPassengerDto 页面
     *       这个页面会返回一个json信息，里面包含当前登陆用户的“常用乘客”信息，但是似乎对订票没什么用？这里暂时不做解析。
     *
     * @return          T --> true
     *                  F --> false
     */
    public boolean getPassengerDto()throws Exception{
        String getPassengerDtoURL = "https://kyfw.12306.cn/otn/confirmPassenger/getPassengerDTOs";
        HttpPost getPassengerDtoRequest = Tools.setRequestHeader(new HttpPost(getPassengerDtoURL), true, false, true);
        Map<String, String> getPasengerDtoData = new HashMap<>();
        getPasengerDtoData.put("_json_att", "");
        getPasengerDtoData.put("REPEAT_SUBMIT_TOKEN", this.repeatSubmitToken);
        getPassengerDtoRequest.setEntity(Tools.doPostData(getPasengerDtoData));
        CloseableHttpResponse response = null;
        try{
            response = this.session.execute(getPassengerDtoRequest);
            if(response.getStatusLine().getStatusCode()==200){
                return true;
            }
        }
        finally {
            if(response!=null){
                response.close();
            }
        }
        return false;
    }

    /**
     *      获取座位号, 从用户输入数组和从initDc页面获取的可预订数组做匹配
     *      匹配成功返回座位ID, 失败返回null
     *
     * @param seatType      用户设置的座位ID(数组)
     * @return              T -- 座位ID
     *                      F -- null
     */
    private String getSeatType(String[] seatType){
        if (!setOptionalSeatType()){
            return null;
        }
        for (String optional: optionalSeatType){
            for (String seat: seatType){
                if (optional.equals(seat)){
                    return optional;
                }
            }
        }
        return null;
    }
    /**
     *      请求 checkOrderInfo 页面
     *
     * @param seatTypeArr       seatTypeArr
     * @param passengerName     passengerName
     * @param documentType      documentType
     * @param documentNumber    documentNumber
     * @param mobile            mobile
     * @return                  checkOrderInfoReturnResult 对象
     *                          -->
     *                          status，passengerTicketStr，oldPassengerStr
     *                          布尔    字符串               字符串
     */
    public CheckOrderInfoReturnResult checkOrderInfo(String[] seatTypeArr,
                                                     String passengerName,
                                                     String documentType,
                                                     String documentNumber,
                                                     String mobile)throws Exception{
        // 创建结果对象
        CheckOrderInfoReturnResult checkOrderInfoReturnResult = new CheckOrderInfoReturnResult();
        String checkOrderInfoURL = "https://kyfw.12306.cn/otn/confirmPassenger/checkOrderInfo";
        // 创建post请求并设置请求头
        HttpPost checkOrderInfoRequest = Tools.setRequestHeader(new HttpPost(checkOrderInfoURL), true, false, false);
        String seatType = getSeatType(seatTypeArr);
        // 座位号为null
        if (seatType == null){
            logger.info("没有符合条件的座位类型");
            checkOrderInfoReturnResult.setStatus(false);
            checkOrderInfoReturnResult.setPassengerTicketStr("");
            checkOrderInfoReturnResult.setOldPassengerStr("");
            checkOrderInfoReturnResult.setSeatType("");
            return checkOrderInfoReturnResult;
        }
        // 拼接请求数据 passengerTicketStr
        String passengerTicketStr = StringUtils.join(new String[] {
                seatType,
                "0",
                "1",
                passengerName,
                documentType,
                documentNumber,
                mobile,
                "N"}, ",");
        // 拼接请求数据 oldPassengerStr
        String oldPassengerStr = StringUtils.join(new String[] {
                passengerName,
                documentType,
                documentNumber,
                "1_"}, ",");
        // 创建请求数据
        Map<String, String> checkOrderInfoData = new HashMap<>();
        checkOrderInfoData.put("cancel_flag", "2");
        checkOrderInfoData.put("bed_level_order_num", "000000000000000000000000000000");
        checkOrderInfoData.put("passengerTicketStr", passengerTicketStr);
        checkOrderInfoData.put("oldPassengerStr", oldPassengerStr);
        checkOrderInfoData.put("tour_flag", "dc");
        checkOrderInfoData.put("randCode", "");
        checkOrderInfoData.put("whatsSelect", "1");
        checkOrderInfoData.put("_json_att", "");
        checkOrderInfoData.put("REPEAT_SUBMIT_TOKEN", this.repeatSubmitToken);
        // 设置请求数据
        checkOrderInfoRequest.setEntity(Tools.doPostData(checkOrderInfoData));
        CloseableHttpResponse response = null;

        try{
            response = this.session.execute(checkOrderInfoRequest);
            if (response.getStatusLine().getStatusCode() == 200){
                String responseText = Tools.responseToString(response);
                boolean status = Tools.getResultJsonStatus(responseText);
                boolean submitStatus = JSONObject.parseObject(responseText)
                        .getJSONObject("data")
                        .getBoolean("submitStatus");
                // 如果 status 和 submitStatus 都为 true
                if(status && submitStatus){
                    checkOrderInfoReturnResult.setStatus(true);
                    checkOrderInfoReturnResult.setPassengerTicketStr(passengerTicketStr);
                    checkOrderInfoReturnResult.setOldPassengerStr(oldPassengerStr);
                    checkOrderInfoReturnResult.setSeatType(seatType);
                    return checkOrderInfoReturnResult;
                }
            }
        }
        finally {
            if(response!=null){response.close();}
        }
        checkOrderInfoReturnResult.setStatus(false);
        checkOrderInfoReturnResult.setPassengerTicketStr("");
        checkOrderInfoReturnResult.setOldPassengerStr("");
        checkOrderInfoReturnResult.setSeatType("");
        return checkOrderInfoReturnResult;
    }

    /**
     *      请求 getQueueCount 页面
     *      这个页面可能会产生一个错误, 因为没有使用 mergerDataToString 这个方法?
     *
     * @param seatType  seatType
     * @return          T --> true
     *                  F --> false
     */
    public boolean getQueueCount(String seatType)throws Exception{
        String getQueueCountURL = "https://kyfw.12306.cn/otn/confirmPassenger/getQueueCount";
        HttpPost getQueueCountRequest = Tools.setRequestHeader(new HttpPost(getQueueCountURL), true, false, true);
        if (this.initHtmlInfo == null){
            return false;
        }
        if ("".equals(this.repeatSubmitToken) || "".equals(this.initHtmlInfo.getLeftTicketStr())){
            return false;
        }
        // 获取TrainDateGMT，之后设置请求数据需要用
        String trainDateGMT = getTrainDateGMT(this.initHtmlInfo.getTrainDateTime());
        // 创建请求数据
        Map<String, String> getQueueCountData = new HashMap<>();
        getQueueCountData.put("_json_att","");
        getQueueCountData.put("from_station_telecode", this.initHtmlInfo.getFromStationTelecode());
        getQueueCountData.put("leftTicket",this.initHtmlInfo.getLeftTicketStr());
        getQueueCountData.put("purpose_codes",this.initHtmlInfo.getPurposeCodes());
        getQueueCountData.put("REPEAT_SUBMIT_TOKEN",this.repeatSubmitToken);
        getQueueCountData.put("seatType",seatType);
        getQueueCountData.put("stationTrainCode",this.initHtmlInfo.getStationTrainCode());
        getQueueCountData.put("toStationTelecode",this.initHtmlInfo.getToStationTelecode());
        getQueueCountData.put("train_date",trainDateGMT);
        getQueueCountData.put("train_location",this.initHtmlInfo.getTrainLocation());
        getQueueCountData.put("train_no",this.initHtmlInfo.getTrainNo());
        // 设置请求数据
        // getQueueCountRequest.setEntity(Tools.doPostData(getQueueCountData));
        getQueueCountRequest.setEntity(new StringEntity(mergerDataToString(getQueueCountData),"utf-8"));

        // this maybe hava a error
        // maybe need a method -> mergerDataToString
        CloseableHttpResponse response = null;
        try{
            response = this.session.execute(getQueueCountRequest);
            if(response.getStatusLine().getStatusCode()==200){
                String responseText = Tools.responseToString(response);
                boolean status = Tools.getResultJsonStatus(responseText);
                if(status){
                    return true;
                }
            }
        }
        finally {
            if(response!=null){
                response.close();
            }
        }
        return false;
    }

    /**
     *      请求 confirmSingleForQueue 页面
     *
     * @param expectSeatNumber      expectSeatNumber
     * @param passengerTicketStr    passengerTicketStr
     * @param oldPassengerStr       oldPassengerStr
     * @return                      T --> true
     *                              F --> false
     */
    public boolean confirmSingleForQueue(String expectSeatNumber,
                                         String passengerTicketStr,
                                         String oldPassengerStr)throws Exception{
        String confirmSingleForQueueURL = "https://kyfw.12306.cn/otn/confirmPassenger/confirmSingleForQueue";
        HttpPost confirmSingleForQueueRequest = Tools.setRequestHeader(new HttpPost(confirmSingleForQueueURL), true, false, true);
        expectSeatNumber = "1" + expectSeatNumber;
        Map<String, String> confirmSingleForQueueData = new HashMap<>();
        confirmSingleForQueueData.put("_json_att","");
        confirmSingleForQueueData.put("choose_seats",expectSeatNumber);
        confirmSingleForQueueData.put("dwAll","N");
        confirmSingleForQueueData.put("key_check_isChange",this.initHtmlInfo.getKeyCheckIsChange());
        confirmSingleForQueueData.put("leftTicketStr",this.initHtmlInfo.getLeftTicketStr());
        confirmSingleForQueueData.put("oldPassengerStr",oldPassengerStr);
        confirmSingleForQueueData.put("passengerTicketStr",passengerTicketStr);
        confirmSingleForQueueData.put("purpose_codes",this.initHtmlInfo.getPurposeCodes());
        confirmSingleForQueueData.put("randCode",this.repeatSubmitToken);
        confirmSingleForQueueData.put("REPEAT_SUBMIT_TOKEN","");
        confirmSingleForQueueData.put("roomType","00");
        confirmSingleForQueueData.put("seatDetailType","000");
        confirmSingleForQueueData.put("train_location",this.initHtmlInfo.getTrainLocation());
        confirmSingleForQueueData.put("whatsSelec","1");
        confirmSingleForQueueRequest.setEntity(Tools.doPostData(confirmSingleForQueueData));

        CloseableHttpResponse response = null;
        try{
            response = this.session.execute(confirmSingleForQueueRequest);
            if (response.getStatusLine().getStatusCode() == 200){
                String responseText = Tools.responseToString(response);
                boolean status = Tools.getResultJsonStatus(responseText);
                boolean submitStatus = JSONObject.parseObject(responseText)
                        .getJSONObject("data")
                        .getBoolean("submitStatus");
                if (status && submitStatus){
                    return true;
                }
            }
        }
        finally {
            if (response!=null){
                response.close();
            }
        }
        return false;
    }


    /**
     *      请求 queryOrderWaitTime 页面
     *      this request return orderID
     *
     * @return  queryOrderWaitTimeReturnResult 对象
     *          status,  orderId
     *          布尔      字符串
     */
    public QueryOrderWaitTimeReturnResult queryOrderWaitTime()throws Exception{
        String queryOrderWaitTimeURL = "https://kyfw.12306.cn/otn/confirmPassenger/queryOrderWaitTime";
        HttpPost queryOrderWaitTimeRequest = Tools.setRequestHeader(new HttpPost(queryOrderWaitTimeURL), true, false, true);

        String orderId = null;
        int waitTime = 10;
        int requestNum = 0;
        while (orderId == null && waitTime > 0){
            requestNum++;
            logger.info(String.format("正在获取等待时间信息......尝试次数：%d", requestNum));
            Map<String, String> queryOrderWaitTimeData = new HashMap<>();
            queryOrderWaitTimeData.put("_json_att", "");
            queryOrderWaitTimeData.put("random", String.valueOf(System.currentTimeMillis()));
            queryOrderWaitTimeData.put("REPEAT_SUBMIT_TOKEN", this.repeatSubmitToken);
            queryOrderWaitTimeData.put("tourFlag", "dc");
            queryOrderWaitTimeRequest.setEntity(Tools.doPostData(queryOrderWaitTimeData));
            CloseableHttpResponse response = null;
            try{
                response = this.session.execute(queryOrderWaitTimeRequest);
                if (response.getStatusLine().getStatusCode() == 200){
                    String responseText = Tools.responseToString(response);
                    boolean status = JSONObject.parseObject(responseText)
                            .getBoolean("status");
                    boolean queryOrderWaitTimeStatus = JSONObject.parseObject(responseText)
                            .getJSONObject("data")
                            .getBoolean("queryOrderWaitTimeStatus");
                    JSONObject jsonData = JSONObject.parseObject(responseText).getJSONObject("data");
                    if (status && queryOrderWaitTimeStatus){
                        orderId = jsonData.getString("orderId");
                        waitTime = jsonData.getIntValue("waitTime");
                        String result = String.format("订单号：%s    等待时间：%d 秒", orderId, waitTime);
                        logger.info(result);
                    }
                }
            }
            finally {
                if (response!=null){
                    response.close();
                }
            }
        }
        QueryOrderWaitTimeReturnResult queryOrderWaitTimeReturnResult = new QueryOrderWaitTimeReturnResult();
        queryOrderWaitTimeReturnResult.setStatus(true);
        queryOrderWaitTimeReturnResult.setOrderId(orderId);
        return queryOrderWaitTimeReturnResult;
    }

    /**
     *      请求 resultOrderForQueue 页面
     *
     * @param orderId   orderId
     * @return          T --> true
     *                  F --> false
     */
    public boolean resultOrderForQueue(String orderId)throws Exception{
        String resultOrderForQueueURL = "https://kyfw.12306.cn/otn/confirmPassenger/resultOrderForDcQueue";
        HttpPost resultOrderForQueueRequest = Tools.setRequestHeader(new HttpPost(resultOrderForQueueURL), true, false, true);
        Map<String, String> resultOrderForQueueData = new HashMap<>();
        resultOrderForQueueData.put("_json_att", "");
        resultOrderForQueueData.put("orderSequence_no", orderId);
        resultOrderForQueueData.put("REPEAT_SUBMIT_TOKEN", this.repeatSubmitToken);
        resultOrderForQueueRequest.setEntity(Tools.doPostData(resultOrderForQueueData));
        CloseableHttpResponse response = null;
        try{
            response = this.session.execute(resultOrderForQueueRequest);
            if (response.getStatusLine().getStatusCode() == 200){
                String responseText = Tools.responseToString(response);
                boolean status = JSONObject.parseObject(responseText)
                        .getBoolean("status");
                boolean submitStatus = JSONObject.parseObject(responseText)
                        .getJSONObject("data")
                        .getBoolean("submitStatus");
                if (status && submitStatus){
                    return true;
                }
            }
        }
        finally {
            if(response!=null) {
                response.close();
            }
        }
        return false;
    }

    /**
     *      提取 passangerTicketList 字段从网页源码
     *
     * @param htmlText      resultBookingTicketHtml 网页源码
     * @return              T --> passangerTicketList 字符串
     *                      F --> ""
     */
    private String getBookingTicketResultMap(String htmlText){
        try {
            String[] scriptList = htmlText.split("\n");
            for (String line: scriptList){
                if(line.contains("var passangerTicketList")){
                    return line.substring(line.indexOf("["), line.length()-1);

                }
            }
        }
        catch (Exception e) {
            logger.info(e.getMessage());
        }
        return "";
    }
    /**
     *      获取订票返回结果页面，并设置 bookingTicketResultInfo
     *
     * @return                  T --> true
     *                          F --> false
     * @throws Exception        异常处理
     */
    public boolean resultBookingTicketHtml() throws Exception{
        String resultBookingTicketURL = "https://kyfw.12306.cn/otn//payOrder/init?random=" + String.valueOf(System.currentTimeMillis());
        HttpPost resultBookingTicketRequest = Tools.setRequestHeader(new HttpPost(resultBookingTicketURL), true, false, false);
        // 创建提交数据
        Map<String, String> resultBookingTicketData = new HashMap<>();
        resultBookingTicketData.put("_json_att", "");
        resultBookingTicketData.put("REPEAT_SUBMIT_TOKEN", this.repeatSubmitToken);
        // 设置提交数据
        resultBookingTicketRequest.setEntity(Tools.doPostData(resultBookingTicketData));
        CloseableHttpResponse response = null;
        try{
            response = this.session.execute(resultBookingTicketRequest);
            if (response.getStatusLine().getStatusCode() == 200){
                String responseText = Tools.responseToString(response);
                String resultPassangerTicketList = getBookingTicketResultMap(responseText);
                if (!resultPassangerTicketList.isEmpty()){
                    // String bookingTicketResultStr = "";
                    this.bookingTicketResultInfo = new BookingTicketResultInfo(resultPassangerTicketList);
                    return true;
                }
            }
        }
        finally {
            if (response!=null) response.close();
        }
        return false;
    }

    /**
     *      返回订票结果字符串，从 bookingTicketResultInfo
     *
     * @return      订票结果
     */
    public String bookingTicketResultToString(){
        // BookingTicketResultInfo bookingTicketResultInfo = new BookingTicketResultInfo(bookingTicketResultStr);
        StringBuilder resultStr = new StringBuilder();
        String[] content = {"席位已锁定，请于30分钟内支付，超时将取消订单！\n",
                "订单号码：" + this.bookingTicketResultInfo.getSequenceNo() + "\n",
                "证件类型：" + this.bookingTicketResultInfo.getPassengerIdTypeName() + "\n",
                "证件号码：" + this.bookingTicketResultInfo.getPassengerIdNo() + "\n",
                "乘客姓名：" + this.bookingTicketResultInfo.getPassengerName() + "\n",
                "车厢号码：" + this.bookingTicketResultInfo.getCoachName() + "\n",
                "座位号码：" + this.bookingTicketResultInfo.getSeatName() + "\n",
                "座位类型：" + this.bookingTicketResultInfo.getSeatTypeName() + "\n",
                "出发站点：" + this.bookingTicketResultInfo.getFromStationName() + "\n",
                "到达站点：" + this.bookingTicketResultInfo.getToStationName() + "\n",
                "列车车次：" + this.bookingTicketResultInfo.getStationTrainCode() + "\n",
                "出发日期：" + this.bookingTicketResultInfo.getStartTrainDate() + "\n",
                "车票价格：" + this.bookingTicketResultInfo.getTicketPrice() + "\n",
                "车票号码：" + this.bookingTicketResultInfo.getTicketNo() + "\n",
                "车票类型：" + this.bookingTicketResultInfo.getTicketTypeName()};
        for(String element: content){
            resultStr.append(element);
        }
        return resultStr.toString();
    }

    /**
     *      订票方法，在实例 BookingTicket 类后直接调用该方法即可实现订票全部逻辑
     *      所有日志记录在这个方法里
     *
     * @param secretStr             列车鉴别码
     * @param trainDate             发车日期
     * @param backTrainDate         返程日期（是个默认值，目前仅实现了购买单程票）
     * @param purposeCode           乘客类型码
     * @param queryFromStationName  出发站
     * @param queryToStationName    到达站
     * @param passengerName         乘客姓名
     * @param documentType          证件类型
     * @param documentNumber        证件号码
     * @param mobile                手机号码
     * @param seatTypeArr           座位类型, 数组
     * @param expectSeatNumber      期望的座位，例如A,B,C,E,F -> A,F 靠窗， C,E 靠过道
     * @return
     */
    public BookingTicketMethodReturnResult bookingTicketMethod(String secretStr,
                                                               String trainDate,
                                                               String backTrainDate,
                                                               String purposeCode,
                                                               String queryFromStationName,
                                                               String queryToStationName,
                                                               String passengerName,
                                                               String documentType,
                                                               String documentNumber,
                                                               String mobile,
                                                               String[] seatTypeArr,
                                                               String expectSeatNumber){
        BookingTicketMethodReturnResult bookingTicketMethodReturnResult = new BookingTicketMethodReturnResult();
        SubmitOrderRequestReturnResult  submitOrderRequestReturnResult;
        CheckOrderInfoReturnResult      checkOrderInfoReturnResult;
        QueryOrderWaitTimeReturnResult  queryOrderWaitTimeReturnResult;

        // 0.
        logger.info("检查登陆状态......");
        try {
            boolean userStauts = Login.checkUserStatus(this.session);
            if (!userStauts){
                return bookingTicketMethodReturnResultFalse(this.session);
            }
        } catch (Exception e) {
            return bookingTicketMethodReturnResultFalse(this.session);
        }

        // 1.
        logger.info("提交订单请求......");
        try {
            submitOrderRequestReturnResult = submitOrderRequest(secretStr,
                                                                trainDate,
                                                                backTrainDate,
                                                                purposeCode,
                                                                queryFromStationName,
                                                                queryToStationName);
            if (!submitOrderRequestReturnResult.getStatus()){
                logger.info("返回信息：" + submitOrderRequestReturnResult.getMessage());
                logger.info("获取提交订单请求失败！");
                return bookingTicketMethodReturnResultFalse(this.session);
            }
        } catch (Exception e) {
            logger.info("获取提交订单请求出错！");
            return bookingTicketMethodReturnResultFalse(this.session);
        }

        // 2.
        logger.info("获取初始化数据并设置......");
        try {
            boolean initDcStatus = initDc();
            if (!initDcStatus){
                logger.info("获取初始化数据失败！");
                return bookingTicketMethodReturnResultFalse(this.session);
            }
        } catch (Exception e) {
            logger.info("获取初始化数据出错！");
            return bookingTicketMethodReturnResultFalse(this.session);
        }

        // 3.
        logger.info("获取乘客dto......");
        try {
            boolean getPassengerDtoStatus = getPassengerDto();
            if (!getPassengerDtoStatus){
                logger.info("获取乘客dto失败！");
                return bookingTicketMethodReturnResultFalse(this.session);
            }
        } catch (Exception e) {
            logger.info("获取乘客dto出错！");
            return bookingTicketMethodReturnResultFalse(this.session);
        }

        // 4
        logger.info("检查订单信息......");
        try {
            checkOrderInfoReturnResult = checkOrderInfo(seatTypeArr,
                                                        passengerName,
                                                        documentType,
                                                        documentNumber,
                                                        mobile);
            if(!checkOrderInfoReturnResult.getStatus()){
                logger.info("检查订单信息失败！");
                return bookingTicketMethodReturnResultFalse(this.session);
            }
        } catch (Exception e) {
            logger.info("检查订单信息出错！");
            return bookingTicketMethodReturnResultFalse(this.session);
        }

        // 5.
        logger.info("获取排队信息......");
        try {
            String seatType = checkOrderInfoReturnResult.getSeatType();
            boolean getQueueCountStatus = getQueueCount(seatType);
            if(!getQueueCountStatus){
                logger.info("获取排队信息失败！");
                return bookingTicketMethodReturnResultFalse(this.session);
            }
        } catch (Exception e) {
            logger.info("获取排队信息出错！");
            return bookingTicketMethodReturnResultFalse(this.session);
        }

        // 6.
        logger.info("获取确认信息......");
        try {
            boolean confirmStatus = confirmSingleForQueue(expectSeatNumber,
                                                          checkOrderInfoReturnResult.getPassengerTicketStr(),
                                                          checkOrderInfoReturnResult.getOldPassengerStr());
            if(!confirmStatus){
                logger.info("获取确认信息失败！");
                return bookingTicketMethodReturnResultFalse(this.session);
            }
        } catch (Exception e) {
            logger.info("获取确认信息出错！");
            return bookingTicketMethodReturnResultFalse(this.session);
        }

        // 7.
        logger.info("查询订单等待时间......");
        try {
            queryOrderWaitTimeReturnResult = queryOrderWaitTime();
            if(!queryOrderWaitTimeReturnResult.getStatus()){
                logger.info("查询订单等待时间失败，但是可能已经成功订票，准备发送通知。");
                bookingTicketMethodReturnResult.setStatus(true);
                bookingTicketMethodReturnResult.setBookingTicketResult("可能已经预定成功，请登陆12306网站查看。");
                return bookingTicketMethodReturnResult;
            }
        } catch (Exception e) {
            logger.info("查询订单等待时间出错，但是可能已经成国公订票，准备发送通知。");
            bookingTicketMethodReturnResult.setStatus(true);
            bookingTicketMethodReturnResult.setBookingTicketResult("可能已经预定成功，请登陆12306网站查看。");
            return bookingTicketMethodReturnResult;
        }

        // 8.
        logger.info("尝试从队列获取订单结果......");
        try {
            boolean resultOrderStatus = resultOrderForQueue(queryOrderWaitTimeReturnResult.getOrderId());
            if(!resultOrderStatus){
                logger.info("从队列获取订单结果失败！");
                return bookingTicketMethodReturnResultFalse(this.session);
            }
        } catch (Exception e) {
            logger.info("从队列获取订单结果出错！");
            return bookingTicketMethodReturnResultFalse(this.session);
        }

        // 9.
        logger.info("获取订票结果......");
        try {
            boolean resultBookingStauts = resultBookingTicketHtml();
            if(!resultBookingStauts){
                logger.info("获取订票结果失败，但是可能已经成功订票，准备发送通知。");
                bookingTicketMethodReturnResult.setStatus(true);
                bookingTicketMethodReturnResult.setBookingTicketResult("可能已经预定成功，请登陆12306网站查看。");
                return bookingTicketMethodReturnResult;
            }
        } catch (Exception e) {
            logger.info("获取订票结果出错，但是可能已经成功订票，准备发送通知。");
            bookingTicketMethodReturnResult.setStatus(true);
            bookingTicketMethodReturnResult.setBookingTicketResult("可能已经预定成功，请登陆12306网站查看。");
            return bookingTicketMethodReturnResult;
        }

        // 10.
        /// logger.info("转换订票结果到字符串......");
        logger.info("发送通知......");
        bookingTicketMethodReturnResult.setStatus(true);
        bookingTicketMethodReturnResult.setBookingTicketResult(bookingTicketResultToString());
        bookingTicketMethodReturnResult.setSession(this.session);
        return bookingTicketMethodReturnResult;
    }

    /**
     *          经实验，该方法的代码在 bookingTicketMethod 方法里使用较多，故单独拉出来写一个
     *      方法以便减少代码量。
     *
     * @param session       当前会话
     * @return              BookingTicketMethodReturnResult 对象
     *                      status, BookingTicketResult, Session
     *                      布尔     字符串               CloseableHttpClient 对象
     */
    private BookingTicketMethodReturnResult bookingTicketMethodReturnResultFalse(CloseableHttpClient session){
        BookingTicketMethodReturnResult bookingTicketMethodReturnResult = new BookingTicketMethodReturnResult();
        bookingTicketMethodReturnResult.setStatus(false);
        bookingTicketMethodReturnResult.setBookingTicketResult("");
        bookingTicketMethodReturnResult.setSession(session);
        return bookingTicketMethodReturnResult;
    }
}

/**
 * return: status, bookingTicketResult, session
 */
class BookingTicketMethodReturnResult{
    public boolean getStatus() {
        return status;
    }

    public void setStatus(boolean status) {
        this.status = status;
    }

    public String getBookingTicketResult() {
        return bookingTicketResult;
    }

    public void setBookingTicketResult(String bookingTicketResult) {
        this.bookingTicketResult = bookingTicketResult;
    }
    public CloseableHttpClient getSession() {
        return session;
    }

    public void setSession(CloseableHttpClient session) {
        this.session = session;
    }
    private boolean status;
    private String  bookingTicketResult;
    private CloseableHttpClient session;
}

/**
 * return: status, passengerTicketStr, oldPassengerStr
 */
class CheckOrderInfoReturnResult{
    public boolean getStatus() {
        return status;
    }

    public void setStatus(boolean status) {
        this.status = status;
    }

    public String getPassengerTicketStr() {
        return passengerTicketStr;
    }

    public void setPassengerTicketStr(String passengerTicketStr) {
        this.passengerTicketStr = passengerTicketStr;
    }

    public String getOldPassengerStr() {
        return oldPassengerStr;
    }

    public void setOldPassengerStr(String oldPassengerStr) {
        this.oldPassengerStr = oldPassengerStr;
    }
    public String getSeatType() {
        return seatType;
    }
    public void setSeatType(String seatType) {
        this.seatType = seatType;
    }

    private boolean status;
    private String  passengerTicketStr;
    private String  oldPassengerStr;
    private String  seatType;





}

/**
 * return, status, message
 */
class SubmitOrderRequestReturnResult{
    public boolean getStatus() {
        return status;
    }

    public void setStatus(boolean status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    private boolean status;
    private String message;
}

/**
 * return: status, orderId
 */
class QueryOrderWaitTimeReturnResult{
    public boolean getStatus() {
        return status;
    }

    public void setStatus(boolean status) {
        this.status = status;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    private boolean status;
    private String  orderId;

}

/**
 *      解析 initDc.html 的返回信息
 *      这个页面会返回订票需要用到的信息
 */
class InitHtmlInfo{

    // private Map<String, String> orderRequestDTO = new HashMap<>();
    // private Map<String, String> queryLeftTicketRequestDTO = new HashMap<>();
    // private Map<String, List<String>> leftDetails = new HashMap<>();

    private String trainDateTime = "";
    private String fromStationTelecode = "";
    private String leftTicketStr = "";
    private String purposeCodes = "";
    private String stationTrainCode = "";
    private String toStationTelecode = "";
    private String trainLocation = "";
    private String trainNo = "";
    private String[] leftDetails;
    private String keyCheckIsChange = "";

    public String getTrainDateTime() {
        return trainDateTime;
    }

    public String getFromStationTelecode() {
        return fromStationTelecode;
    }

    public String getLeftTicketStr() {
        return leftTicketStr;
    }

    public String getPurposeCodes() {
        return purposeCodes;
    }

    public String getStationTrainCode() {
        return stationTrainCode;
    }

    public String getToStationTelecode() {
        return toStationTelecode;
    }

    public String getTrainLocation() {
        return trainLocation;
    }

    public String getTrainNo() {
        return trainNo;
    }

    public String[] getLeftDetails() {
        return leftDetails;
    }

    public String getKeyCheckIsChange() {
        return keyCheckIsChange;
    }

    // 构造器
    public InitHtmlInfo(String initHtmlStr){
        // 替换单引号到双引号
        initHtmlStr = initHtmlStr.replace("'","\"");
        // 创建Json对象
        JSONObject allJsonData = JSONObject.parseObject(initHtmlStr);
        JSONObject orderRequestDTOJsonData = allJsonData.getJSONObject("orderRequestDTO");
        JSONObject orderRequestDTOTrainDateTimeJsonData = orderRequestDTOJsonData.getJSONObject("train_date");
        JSONObject queryLeftTicketRequestDTOJsonData = allJsonData.getJSONObject("queryLeftTicketRequestDTO");
        // 设置属性值
        this.trainDateTime = String.valueOf(orderRequestDTOTrainDateTimeJsonData.getLong("time"));
        this.fromStationTelecode = orderRequestDTOJsonData.getString("from_station_telecode");
        this.leftTicketStr = allJsonData.getString("leftTicketStr");
        this.purposeCodes = allJsonData.getString("purpose_codes");
        this.stationTrainCode = orderRequestDTOJsonData.getString("station_train_code");
        this.toStationTelecode = orderRequestDTOJsonData.getString("to_station_telecode");
        this.trainLocation = allJsonData.getString("train_location");
        this.trainNo = queryLeftTicketRequestDTOJsonData.getString("train_no");
        this.leftDetails = allJsonData
                .getString("leftDetails")
                .replace("[","")
                .replace("]", "")
                .replace("\"","")
                .split(",");
        this.keyCheckIsChange = allJsonData.getString("key_check_isChange");
        // for (Map.Entry<String, Object> element: orderRequestDTOJsonData.entrySet()){
        //     String key = element.getKey();
        //     String value = String.valueOf(element.getValue());
        //     this.orderRequestDTO.put(key, value);
        //     // System.out.println(element.getKey() + "-->" + element.getValue().toString());
        // }
    }
    // 测试
    public static void main(String[] args){
        String jsonStr = "{'cardTypes':[{'end_station_name':null,'end_time':null,'id':'1','start_station_name':null,'start_time':null,'value':'\\u4E2D\\u56FD\\u5C45\\u6C11\\u8EAB\\u4EFD\\u8BC1'},{'end_station_name':null,'end_time':null,'id':'C','start_station_name':null,'start_time':null,'value':'\\u6E2F\\u6FB3\\u5C45\\u6C11\\u6765\\u5F80\\u5185\\u5730\\u901A\\u884C\\u8BC1'},{'end_station_name':null,'end_time':null,'id':'G','start_station_name':null,'start_time':null,'value':'\\u53F0\\u6E7E\\u5C45\\u6C11\\u6765\\u5F80\\u5927\\u9646\\u901A\\u884C\\u8BC1'},{'end_station_name':null,'end_time':null,'id':'B','start_station_name':null,'start_time':null,'value':'\\u62A4\\u7167'},{'end_station_name':null,'end_time':null,'id':'H','start_station_name':null,'start_time':null,'value':'\\u5916\\u56FD\\u4EBA\\u6C38\\u4E45\\u5C45\\u7559\\u8EAB\\u4EFD\\u8BC1'}],'isAsync':'1','key_check_isChange':'3030CBE49CE72CE8473173EA240AD0F2CEF787494414A57119C99608','leftDetails':['\\u4E00\\u7B49\\u5EA7(110.00\\u5143)\\u65E0\\u7968','\\u4E8C\\u7B49\\u5EA7(69.00\\u5143)1\\u5F20\\u7968','\\u65E0\\u5EA7(69.00\\u5143)\\u6709\\u7968'],'leftTicketStr':'ScF7FOCtg1d9I3%2BdC8tRYkjBRGiLqBBvfaQhA6T9DdkS1evE','limitBuySeatTicketDTO':{'seat_type_codes':[{'end_station_name':null,'end_time':null,'id':'O','start_station_name':null,'start_time':null,'value':'\\u4E8C\\u7B49\\u5EA7'}],'ticket_seat_codeMap':{'3':[{'end_station_name':null,'end_time':null,'id':'O','start_station_name':null,'start_time':null,'value':'\\u4E8C\\u7B49\\u5EA7'}],'2':[{'end_station_name':null,'end_time':null,'id':'O','start_station_name':null,'start_time':null,'value':'\\u4E8C\\u7B49\\u5EA7'}],'1':[{'end_station_name':null,'end_time':null,'id':'O','start_station_name':null,'start_time':null,'value':'\\u4E8C\\u7B49\\u5EA7'}],'4':[{'end_station_name':null,'end_time':null,'id':'O','start_station_name':null,'start_time':null,'value':'\\u4E8C\\u7B49\\u5EA7'}]},'ticket_type_codes':[{'end_station_name':null,'end_time':null,'id':'1','start_station_name':null,'start_time':null,'value':'\\u6210\\u4EBA\\u7968'},{'end_station_name':null,'end_time':null,'id':'2','start_station_name':null,'start_time':null,'value':'\\u513F\\u7AE5\\u7968'},{'end_station_name':null,'end_time':null,'id':'3','start_station_name':null,'start_time':null,'value':'\\u5B66\\u751F\\u7968'},{'end_station_name':null,'end_time':null,'id':'4','start_station_name':null,'start_time':null,'value':'\\u6B8B\\u519B\\u7968'}]},'maxTicketNum':'5','orderRequestDTO':{'adult_num':0,'apply_order_no':null,'bed_level_order_num':null,'bureau_code':null,'cancel_flag':null,'card_num':null,'channel':null,'child_num':0,'choose_seat':null,'disability_num':0,'end_time':{'date':1,'day':4,'hours':10,'minutes':26,'month':0,'seconds':0,'time':8760000,'timezoneOffset':-480,'year':70},'exchange_train_flag':'0','from_station_name':'\\u897F\\u5B89\\u5317','from_station_telecode':'EAY','get_ticket_pass':null,'id_mode':'Y','isShowPassCode':null,'leftTicketGenTime':null,'order_date':null,'passengerFlag':null,'realleftTicket':null,'reqIpAddress':null,'reqTimeLeftStr':null,'reserve_flag':'A','seat_detail_type_code':null,'seat_type_code':null,'sequence_no':null,'start_time':{'date':1,'day':4,'hours':9,'minutes':15,'month':0,'seconds':0,'time':4500000,'timezoneOffset':-480,'year':70},'start_time_str':null,'station_train_code':'D2508','student_num':0,'ticket_num':0,'ticket_type_order_num':null,'to_station_name':'\\u8FD0\\u57CE\\u5317','to_station_telecode':'ABV','tour_flag':'dc','trainCodeText':null,'train_date':{'date':23,'day':3,'hours':0,'minutes':0,'month':0,'seconds':0,'time':1548172800000,'timezoneOffset':-480,'year':119},'train_date_str':null,'train_location':null,'train_no':'4f000D250810','train_order':null,'varStr':null},'purpose_codes':'00','queryLeftNewDetailDTO':{'BXRZ_num':'-1','BXRZ_price':'0','BXYW_num':'-1','BXYW_price':'0','EDRZ_num':'-1','EDRZ_price':'0','EDSR_num':'-1','EDSR_price':'0','ERRB_num':'-1','ERRB_price':'0','GG_num':'-1','GG_price':'0','GR_num':'-1','GR_price':'0','HBRW_num':'-1','HBRW_price':'0','HBRZ_num':'-1','HBRZ_price':'0','HBYW_num':'-1','HBYW_price':'0','HBYZ_num':'-1','HBYZ_price':'0','RW_num':'-1','RW_price':'0','RZ_num':'-1','RZ_price':'0','SRRB_num':'-1','SRRB_price':'0','SWZ_num':'-1','SWZ_price':'0','TDRZ_num':'-1','TDRZ_price':'0','TZ_num':'-1','TZ_price':'0','WZ_num':'47','WZ_price':'00690','WZ_seat_type_code':'O','YB_num':'-1','YB_price':'0','YDRZ_num':'-1','YDRZ_price':'0','YDSR_num':'-1','YDSR_price':'0','YRRB_num':'-1','YRRB_price':'0','YW_num':'-1','YW_price':'0','YYRW_num':'-1','YYRW_price':'0','YZ_num':'-1','YZ_price':'0','ZE_num':'1','ZE_price':'00690','ZY_num':'0','ZY_price':'01100','arrive_time':'1026','control_train_day':'','controlled_train_flag':null,'controlled_train_message':null,'day_difference':null,'end_station_name':null,'end_station_telecode':null,'from_station_name':'\\u897F\\u5B89\\u5317','from_station_telecode':'EAY','is_support_card':null,'lishi':'01:11','seat_feature':'','start_station_name':null,'start_station_telecode':null,'start_time':'0915','start_train_date':'','station_train_code':'D2508','to_station_name':'\\u8FD0\\u57CE\\u5317','to_station_telecode':'ABV','train_class_name':null,'train_no':'4f000D250810','train_seat_feature':'','yp_ex':''},'queryLeftTicketRequestDTO':{'arrive_time':'10:26','bigger20':'Y','exchange_train_flag':'0','from_station':'EAY','from_station_name':'\\u897F\\u5B89\\u5317','from_station_no':'01','lishi':'01:11','login_id':null,'login_mode':null,'login_site':null,'purpose_codes':'00','query_type':null,'seatTypeAndNum':null,'seat_types':'OMO','start_time':'09:15','start_time_begin':null,'start_time_end':null,'station_train_code':'D2508','ticket_type':null,'to_station':'ABV','to_station_name':'\\u8FD0\\u57CE\\u5317','to_station_no':'03','train_date':'20190123','train_flag':null,'train_headers':null,'train_no':'4f000D250810','useMasterPool':true,'useWB10LimitTime':true,'usingGemfireCache':false,'ypInfoDetail':'ScF7FOCtg1d9I3%2BdC8tRYkjBRGiLqBBvfaQhA6T9DdkS1evE'},'tour_flag':'dc','train_location':'Y2'}";
        InitHtmlInfo initHtmlInfo = new InitHtmlInfo(jsonStr);
        System.out.println(initHtmlInfo.getTrainDateTime());
        System.out.println(initHtmlInfo.getFromStationTelecode());
        System.out.println(initHtmlInfo.getLeftTicketStr());
        System.out.println(initHtmlInfo.getPurposeCodes());
        System.out.println(initHtmlInfo.getStationTrainCode());
        System.out.println(initHtmlInfo.getToStationTelecode());
        System.out.println(initHtmlInfo.getTrainLocation());
        System.out.println(initHtmlInfo.getTrainNo());
        System.out.println(initHtmlInfo.getLeftDetails()[0]);
        System.out.println(initHtmlInfo.getKeyCheckIsChange());
        for (String element: initHtmlInfo.getLeftDetails()){
            System.out.println(element);
        }
    }
}

/**
 *      解析 https://kyfw.12306.cn/otn//payOrder/init 的返回信息
 *      这个页面会返回订票的结果信息
 */
class BookingTicketResultInfo{

    public String getSequenceNo() {
        return sequenceNo;
    }

    public String getPassengerIdTypeName() {
        return passengerIdTypeName;
    }

    public String getPassengerIdNo() {
        return passengerIdNo;
    }

    public String getPassengerName() {
        return passengerName;
    }

    public String getCoachName() {
        return coachName;
    }

    public String getSeatName() {
        return seatName;
    }

    public String getSeatTypeName() {
        return seatTypeName;
    }

    public String getFromStationName() {
        return fromStationName;
    }

    public String getToStationName() {
        return toStationName;
    }

    public String getStationTrainCode() {
        return stationTrainCode;
    }

    public String getStartTrainDate() {
        return startTrainDate;
    }

    public String getTicketPrice() {
        return ticketPrice;
    }

    public String getTicketNo() {
        return ticketNo;
    }

    public String getTicketTypeName() {
        return ticketTypeName;
    }

    private String sequenceNo;
    private String passengerIdTypeName;
    private String passengerIdNo;
    private String passengerName;
    private String coachName;
    private String seatName;
    private String seatTypeName;
    private String fromStationName;
    private String toStationName;
    private String stationTrainCode;
    private String startTrainDate;
    private String ticketPrice;
    private String ticketNo;
    private String ticketTypeName;

    public BookingTicketResultInfo(String bookingTicketResultStr){
        bookingTicketResultStr = bookingTicketResultStr.replace("'", "\"");
        JSONArray bookingTicketResultList = JSONArray.parseArray(bookingTicketResultStr);
        JSONObject jsonData = bookingTicketResultList.getJSONObject(0);
        JSONObject jsonPassengerDTO = jsonData.getJSONObject("passengerDTO");
        JSONObject jsonPayOrderString = jsonData.getJSONObject("payOrderString");
        JSONObject jsonStationDTO = jsonData.getJSONObject("stationTrainDTO");

        this.sequenceNo = jsonData.getString("sequence_no");
        this.passengerIdTypeName = jsonPassengerDTO.getString("passenger_id_type_name");

        this.passengerIdNo = jsonPassengerDTO.getString("passenger_id_no");
        this.passengerName = jsonPassengerDTO.getString("passenger_name");

        this.coachName = jsonData.getString("coach_name");
        this.seatName = jsonData.getString("seat_name");
        this.seatTypeName = jsonData.getString("seat_type_name");

        this.fromStationName = jsonStationDTO.getString("from_station_name");
        this.toStationName = jsonStationDTO.getString("to_station_name");
        this.stationTrainCode = jsonStationDTO.getString("station_train_code");

        this.startTrainDate = jsonData.getString("start_train_date_page");

        this.ticketPrice = jsonData.getString("str_ticket_price_page");
        this.ticketNo = jsonData.getString("ticket_no");
        this.ticketTypeName = jsonData.getString("ticket_type_name");

    }

}
