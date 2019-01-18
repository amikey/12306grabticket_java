import com.alibaba.fastjson.JSONObject;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 *      这个类主要用于查询票的信息，过滤符合条件的票，该类全是静态方法
 *
 *
 *      公共方法列表：            作用：
 *      trainInfoJsonList       根据输入条件查询列车，查询结果是一个List，每个List里包含一个Map
 *      trainInfoMap            从 trainInfoJsonList 这个方法里过滤出一个符合条件的Map
 *
 *      可能会用到的参数：
 *      bookingAfterTime        用户设置的列车时间上界
 *      bookingBeforeTime       用户设置的列车时间下界
 *      trainDate               列车日期
 *      fromStation             出发站
 *      toStation               到达站
 *      purposeCode             乘客类型码   默认：Adult
 *
 *      userSetTrainName        用户指定的列车号，例如：D2565
 *
 *      PS: 方法 trainInfoMap 里返回的 Map 里的 trainDate 已经格式化为：yyyy-MM-dd
 */
public class QueryTicketInfo {

    private static CloseableHttpClient session = Tools.getSession(30000);
    private static long queryNum = 0L;

    /**
     * 设置日志记录
     */
    private static final Logger logger = LoggerFactory.getLogger(QueryTicketInfo.class);


    /**
     *      这个方法包含了查找匹配条件的列车信息的全部方法
     *
     * @param bookingAfterTime      bookingAfterTime
     * @param bookingBeforeTime     bookingBeforeTime
     * @param trainDate             trainDate
     * @param fromStation           fromStation
     * @param toStation             toStation
     * @param purposeCode           purposeCode
     * @param userSetTrainName      userSetTrainName
     * @return                      Map
     */
    public static Map<String, String> trainInfoMethod(String bookingAfterTime,
                                                      String bookingBeforeTime,
                                                      String trainDate,
                                                      String fromStation,
                                                      String toStation,
                                                      String purposeCode,
                                                      String userSetTrainName) throws Exception{
        Date t1 = new Date();
        Map<String, String> resultMap = trainInfoMap(trainInfoJsonList(bookingAfterTime, bookingBeforeTime, trainDate, fromStation, toStation, purposeCode), userSetTrainName);
        Date t2 = new Date();
        queryNum++;
        double intervalSecond = ((double)t2.getTime() - (double)t1.getTime()) / 1000;
        logger.info(String.format("查询符合条件的列车信息......    尝试次数：%1$5d    耗时：%2$5.2f 秒", queryNum, intervalSecond));
        // if (resultMap != null){
        //     session.close();
        // }
        return resultMap;
    }
    /**
     *      获取列车信息，返回一个字符串数组
     *
     * @param bookingAfterTime      列车时间上界
     * @param bookingBeforeTime     列车时间下界
     * @param trainDate             列车日期
     * @param fromStation           出发站
     * @param toStation             到达站
     * @param purposeCode           乘客类型码
     * @return                      T --> List, 每个元素为一个 map
     *                              F --> null
     */
    private static List<Map<String, String>> trainInfoJsonList(String bookingAfterTime,
                                                              String bookingBeforeTime,
                                                              String trainDate,
                                                              String fromStation,
                                                              String toStation,
                                                              String purposeCode) throws Exception{
        int httpPassStatusCode = 200;
        // 每次请求都创建session
        // CloseableHttpClient session = Tools.getSession(30000);
        // String baseURL  = "https://kyfw.12306.cn/otn/leftTicket/";
        String queryURL = "https://kyfw.12306.cn/otn/leftTicket/queryZ";
        URI queryURLFirst = new URIBuilder("https://kyfw.12306.cn/otn/leftTicket/queryZ")
                .setParameter("leftTicketDTO.train_date", trainDate)
                .setParameter("leftTicketDTO.from_station", getCityId(fromStation))
                .setParameter("leftTicketDTO.to_station", getCityId(toStation))
                .setParameter("purpose_codes", purposeCode)
                .build();
        HttpGet queryRequest = Tools.setRequestHeader(new HttpGet(queryURLFirst), true, false, true);
        CloseableHttpResponse response = null;
        try{
            response = session.execute(queryRequest);
            if (response.getStatusLine().getStatusCode() == httpPassStatusCode){
                String responseText = Tools.responseToString(response);
                boolean status = JSONObject.parseObject(responseText).getBoolean("status");
                if(!status){
                    String baseURL  = "https://kyfw.12306.cn/otn/leftTicket/";
                    String[] queryInterFaceList = JSONObject.parseObject(responseText).getString("c_url").split("/");
                    String queryInterFace = queryInterFaceList[queryInterFaceList.length-1];
                    URI queryURLSecond = new URIBuilder(baseURL + queryInterFace)
                            .setParameter("leftTicketDTO.train_date", trainDate)
                            .setParameter("leftTicketDTO.from_station", getCityId(fromStation))
                            .setParameter("leftTicketDTO.to_station", getCityId(toStation))
                            .setParameter("purpose_codes", purposeCode)
                            .build();
                    queryRequest = Tools.setRequestHeader(new HttpGet(queryURLSecond), true, false, true);
                    response = session.execute(queryRequest);
                        if(response.getStatusLine().getStatusCode() == httpPassStatusCode){
                            responseText = Tools.responseToString(response);
                        }
                }
                String result = JSONObject.parseObject(responseText).getJSONObject("data").getString("result");
                // System.out.println(result);
                // logger.info(result);
                return resultListMap(result, bookingAfterTime, bookingBeforeTime);
            }
        }
        finally {
            if(response!=null){
                response.close();
            }
            // session.close();
        }
        return null;
    }
    /**
     *      获取符合条件的列车信息，返回一个Map
     *      如果 userSetTrainName 为空的话取最早一班
     *
     * @param trainInfoMapList      列车信息列表
     * @param userSetTrainName      用户指定列车号， 如：D2565
     * @return                      T --> 列车信息Map
     *                              F --> 没有返会Null
     */
    private static Map<String, String> trainInfoMap(List<Map<String, String>> trainInfoMapList, String userSetTrainName){
        // trainInfoMapList 为 null 或 元素数为 0 返回 null
        if (null == trainInfoMapList || trainInfoMapList.size() == 0){
            return null;
        }
        for (Map<String, String> element: trainInfoMapList){
            String trainName = element.get("trainName");
            if (!"".equals(userSetTrainName)){
                // 匹配到 userSetTrainName 返回对应的 map
                if (userSetTrainName.equals(trainName)){
                    return element;
                }
            }
        }
        // userSetTrainName 不为空并且没匹配到返回 null
        if (!"".equals(userSetTrainName)){
            return null;
        }
        // userSetTrainName 为空返回列表第一个 map
        return trainInfoMapList.get(0);
    }

    /**
     *      格式化日期
     *
     *      例如： 20190116 --> 2019-01-16
     *
     * @param trainDate     日期
     * @return              日期
     */
    private static String formatDate(String trainDate){
        StringBuilder trainDateNew = new StringBuilder();
        trainDateNew.append(trainDate);
        trainDateNew.insert(4, "-");
        trainDateNew.insert(7, "-");
        return trainDateNew.toString();
    }

    /**
     *      从字符串提取列车查询结果，返回一个List，每个元素为一个Map
     *
     * @param resultStr     查询 result
     * @return              List, 每个元素为一个Map
     */
    private static List<Map<String, String>> resultListMap(String resultStr, String afterTime, String beforeTime) throws Exception{
        List<Map<String, String>> list = new ArrayList<>();
        String[] resultlList = resultStr
                .replace("[", "")
                .replace("]", "")
                .replace("\"", "")
                .split(",");
        for (String element: resultlList){
            String[] elementList = element.split("\\|");
            if (!isTrainTimePass(elementList[8], afterTime, beforeTime) || "".equals(elementList[0])){
                continue;
            }
            Map<String, String> map = new HashMap<>();
            map.put("secretStr", elementList[0]);
            map.put("trainNo", elementList[2]);
            map.put("trainName", elementList[3]);
            map.put("startTime", elementList[8]);
            map.put("endTime", elementList[9]);
            map.put("continueTime", elementList[10]);
            map.put("trainStatus", elementList[11]);
            map.put("trainDate", formatDate(elementList[13]));
            map.put("startNum", elementList[16]);
            map.put("endNum", elementList[17]);
            map.put("trainId", elementList[35]);
            list.add(map);
        }
        return list;
    }

    /**
     *      判断列车发车时间是否符合条件
     *
     *      用户设置一个时间区间，判断列车时间是否在区间里
     *
     * @param startTime     列车发车时间
     * @param beforeTime    用户设置的列车时间上界
     * @param afterTime     用户设置的列车时间下界
     * @return              T --> true
     *                      F --> false
     * @throws Exception
     */
    private static Boolean isTrainTimePass(String startTime, String afterTime, String beforeTime) throws Exception{
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("HH:mm");
        Date startTimeTypeDate = simpleDateFormat.parse(startTime);
        Date afterTimeTypeDate = simpleDateFormat.parse(afterTime);
        Date beforeTimeTypeDate = simpleDateFormat.parse(beforeTime);

        if (beforeTimeTypeDate.getTime() >= startTimeTypeDate.getTime() && afterTimeTypeDate.getTime() <= startTimeTypeDate.getTime()){
            return true;
        }
        return false;
    }
    /**
     *      输入城市名获取城市id
     *
     * @param cityName      城市名
     * @return              城市id
     */
    private static String getCityId(String cityName){
        String[] cityIdList = Tools.readFileText("./city_id").split("\n");
        for (String line: cityIdList){
            String[] cityId = line.split("\\|");
            if (cityName.equals(cityId[0])){
                return cityId[1];
            }
        }
        return null;
    }
}
