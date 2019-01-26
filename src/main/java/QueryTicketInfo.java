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
import java.util.concurrent.ThreadPoolExecutor;

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
    private static long lastTime = System.currentTimeMillis();
    private static double intervalTime = 0;

    /**
     * 设置日志记录
     */
    private static final Logger logger = LoggerFactory.getLogger(QueryTicketInfo.class);

    /**
     *  测试
     *
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception{
        // String testStr = "[\"b4WfxVWyJLAmmBZMhAl55c7DHW9Jl8wsokgkzA80igHMcC7bcPo4E3uEIQfXWgInKS%2FLZPEx0GlK%0APiXjK1VlH1skCQoN7Ug1N4da9qta%2FnohEeBcrsNTybLTN0LohcnKvoN60n0F3JNGNwbMFDitrqt6%0AePNwADWisjMqX8G0sB6O5C2y%2BjWkLpdT3LDlEm%2Ft%2FHjPBCS%2BTNpe4wlyoDCQcFdoLZSolElmrJuG%0A0JIe4o5hCwcOOoigDsLrRUUFk13SyhnKAQL3ITSuDm%2BKQ5C0KalZLBjvFVQOtl7unVz1CHx6Xb2X%0A|预订|4f000D250810|D2508|EAY|IPV|EAY|ABV|09:15|10:26|01:11|Y|n9CVJ7UaIIFHzloUkl90%2FD%2BTQyTTttiSrue0Y0kkd6X1S7HK|20190116|3|Y2|01|03|1|0|||||||11||||无|无|||O0M0O0|OMO|0|0|null\",\"lGCSdqr%2BqkWPAWSWLWRoAqolwrQFqDCvKbm6SFIPNwoyDmrS3Cu1zQJHsLvKZFuEq4HbW%2BrwrHsu%0AWH3LduwMvSppQhUJ3MOsdyQR4HSFslnCRl6lvCBhowCclsPiXkPxEtCPNDpHERvnAmfbn%2BsLSn8i%0AQkKfEAdPwzGXDwN8yzS0J46nzxVOfpgGb2wy1WXki0CC8iRCA9RC6gg7n270YdnPdmkCQYNN206M%0AV0BiVN6DeULlp6Ij5OE8XzniUi5S%2BvWKC3viWY6EBEZicR9HrVGZ5CpdVZ%2FQks13BwJWX4wGpmDm%0A|预订|4f000D253803|D2538|EAY|TNV|EAY|ABV|10:41|12:07|01:26|Y|3RliWbk6tRqF0BEoNzC01YpKVhozz9fSzsgKbrcEXkdtUxnU|20190116|3|Y2|01|05|1|0|||||||无||||有|有|||O0M0O0|OMO|1|0|null\",\"yQ6Nz9OPoR%2F33Qj%2BH9yR5Icr0d%2BQcNol6sI83Or5hdA0NGYxEV0jjIqE%2FdV%2FDu%2Fbo8Tn9jOYQH6Y%0AjZu%2FHHjGa03dRQsCjXrf1gzHOy7OfDO3SAXm1Awyu9GWNYHabZKDCkeB26IHZpNYhsml0lyR7nkS%0AFtgXYWW8jGdzlWpgMhO7Z0V8PwyyY506pyEjmhxCSvWkzNGYtolRHstcZDz4GvIa2ZVi7begyco5%0A1MtwyWDfue%2FDMdZBUV0sSqwXBm0trWpFzmMBkN5I3UZDxYDKvnH92GTFzWx1KJdyAp%2B2s92MK%2B5w%0A|预订|4f000D251206|D2512|EAY|TNV|EAY|ABV|11:08|12:27|01:19|Y|BgfQSVc5n2LW4YzHkHEanf6KGilGXOCuguPkpV%2FvgxH06Gna|20190116|3|Y2|01|04|1|0|||||||5||||有|有|||O0M0O0|OMO|0|0|null\",\"BD4EE14h7DIPaBr63aGjH4gTT7dU9fSConELIT%2FTuJetBAw6MhEqGpSSwoTwToFKOzdHOn1UUvd2%0AsqrESAri7GSK6vhIF6Pb%2BBek6lm%2FA%2B34c072FcUG74q73SHi5ikIh07CZVKICupwRemQWV3Q5cAc%0ACIAoMp2kn6ssnC%2BxprLJIzyYu595enAI5W3IsVT18rKM5MaSFuBCHQEqBcWrb7r%2F9Bpwor17D2fg%0AxjNiwazXysEK1TU3DB68reY1BMg%2BscOOD7tCrF%2BwXqfDD5n0HlOsZRRXAA%2BPnHuN4bpayoWnf58f%0A|预订|4f000D251605|D2516|EAY|TNV|EAY|ABV|12:10|13:37|01:27|Y|6fzbT6BhaTLtnxko%2Fl5UYLR5pSmvaUsC8AVpYdObDSVNPKiE|20190116|3|Y2|01|05|1|0|||||||无||||有|有|||O0M0O0|OMO|0|0|null\",\"|预订|4f000D252007|D2520|EAY|TNV|EAY|ABV|13:25|14:37|01:12|N|iPlHSGxzawpH6ea1PLicWCtEVVZK8Nacu0pI5uhG8482ohFp|20190116|3|Y2|01|03|1|0|||||||无||||无|无|||O0M0O0|OMO|0|0|null\",\"|预订|77000D195702|D1960|CXW|TNV|EAY|ABV|15:30|16:48|01:18|N|TXju8vCbrtxP%2F7vTFBsFMtAaRL4Ku7oZ%2Fl9MupcF8FrJCswk|20190116|3|W2|09|12|1|0|||||||||||无|无|无||O0M090|OM9|1|0|null\",\"|预订|4g000D256204|D2562|BBY|TNV|EAY|ABV|16:07|17:39|01:32|N|iPlHSGxzawpH6ea1PLicWCtEVVZK8Nacu0pI5uhG8482ohFp|20190116|3|Y2|04|08|1|0|||||||无||||无|无|||O0M0O0|OMO|1|0|null\",\"93LlocdQilnUF6gzvKehPR6QNf6w2NjPpz3exLswRuJtCaMmoYnbza1BEja9vy51f3lpCSYguKVB%0A9k6aNVphbYZ%2FGXGr6eJpKtL2cJkcuOSAQ5xVF9LVlp3mLleFMlwBABpyAJTu7XjTzcJJfxw59wfX%0A0%2BR9jMhLPKMNlZVLqvRucd4XXpzxBundyTlq0BpdgqIO63bqXOY3Qxme87NCDrUvg6UaA9vVz1wk%0AqNJhOvnqbabbihv9sNejfqfI72TQSH9SqKtHVCqBIQ1dWn6pCITgtByBwmbLeXyABPAvSOghoryE%0A|预订|4f000D252807|D2528|EAY|TNV|EAY|ABV|17:25|18:52|01:27|Y|Sh2L6vPHmWncO2ROZOuYZ3GQh8by4C4nJ%2Bh8p6b8%2FJIOzOzx|20190116|3|Y2|01|05|1|0|||||||无||||有|有|||O0M0O0|OMO|1|0|null\",\"V7d%2F0IliuOB8rC6Sh034RaeZg1jNoafJSe7ErQygyBWbCybLVMikN51OpKKfRzKcMTxrRHplyFJe%0AmaiZ7VdaQut31nwEEGwwn%2FwmEosW9s4dnHwMt%2F%2B2f0C0h5%2FFIcIlMmq5kzyLlOlZflz3hCk5%2B%2FDj%0AN3wJgigHC8jVkKzrz75f4fPkEI8TGfXLCftObuhMfP8k1zZiBTV5tGgQVFz6F4rmXid17fb0ldml%0ASqd6WEqPN4R%2B0pCtgmO6XBKP2URizNdh5ewjoPK9FWvtKT%2FIPmTO9ZKXSeEiqmbz6HImdzs3NBMY%0A|预订|4f000D253005|D2530|EAY|TNV|EAY|ABV|17:43|19:09|01:26|Y|8A0BPDJZRScGhFE%2BzvgLRTw6dVlZyGq836H%2BganMCslavwxj|20190116|3|Y2|01|05|1|0|||||||有||||有|有|||O0M0O0|OMO|1|0|null\",\"JHq6P7Oo%2FoNKjWZRVlgXDO35j5kvfUPCRcXNy3Kn47s9B%2FxnzRBMEtUP%2FB5L7Yj9ei4J%2BZBvT29G%0AUxJoi7PGOqcywk7QQalYQ2dzDO38DROLVdeXGiosOkE4%2B2C%2BgB%2F7R%2FZq9y%2B6XNMOWQ2mCd%2B6m12C%0A2eYo9Ge2tFiscqoEjpIeTZ4biVG4pJdGXBTOYmi1u2UN1YPw0hB7HuWxR6OTMuIX6I3qBU6xW4W9%0A08tYA7M0rmk4MtfXPs7shhL0Tg9TYRyrZesc%2FIT%2FIh0DMVVAOccvjj6P6bjHkJOkvp6Q3eYNtMRO%0A|预订|8d000D256802|D2568|LAJ|TNV|EAY|ABV|18:14|19:34|01:20|Y|6F0ycaWnFELoAQ1I8dchUlr2%2F3v2dDvVNTE6TRqyEd%2Bvm0Cb|20190116|3|J1|06|09|1|0|||||||有||||3|2|||O0M0O0|OMO|1|0|null\",\"qvcJGA8LgSMLrgqDIXfQRkF0CAGEsUdKpOIE%2BAJ8Eh9HjIxXEKIp%2BnIkVEMfi5cy6EpoV5ZQRWAX%0AQxmLJElPJLRhSBiaZUuHjIyhsuULIHfK5yk4f2%2FSObSnusObNyx2FnSp%2BO%2Ft6Ug4jep6V85ZAZYH%0AjaSr8DVgzcEF4vdJejfjkXoVhbjqXe8RyG5SIfbi2IuErQUd2QLrh5WQZBdVGTlQxWvt0ijBPs2c%0AY2RxP7wZUbBdZuTPsB4p07I7j938A6PEKYLNuxiNT%2B%2BSZfKsyacjvK1DDopgsmvjufZCO3WpBeEJ%0Ao8E0cw%3D%3D|预订|770000K6900C|K690|CUW|TYV|CAY|YNV|19:03|00:23|05:20|Y|0ljjhCa6YHj8%2FhnVTJzs4eV2FEwAwT%2BfiRk0%2FMVqBZ%2FsjeNRqfTApidEyT0%3D|20190116|3|W2|12|16|0|0||||无|||无||19|有|||||10401030|1413|1|0|null\",\"xpeJV97XV39chFzTlyYEGvBFjFy7zu%2BGr3UvmR%2BR75m9AnIgpIgKq7rQ9yJ8Yn6SBwjqULTFwh%2B%2F%0AwH5WGIZFV4G4eabjxM%2FjM0AFFqEj6GUJi%2FhLd1xRLaLA5wrRDXGeriNCsQwcbSoCwaJ%2B7R%2FnHm7g%0AibuwRfVy3pvkLft03lybtQC52pDlGbr%2BpnhhVRULGsNJ4KPf3hSvm5JIYiR%2FxnIc1lUmPpkAIdAh%0ApJcFgHINdqlM0UWHWzaQJFJaiHg8WHisMvhN%2BE8eIVzTEVh0dQozYtoFziKut3dskXZPFtNTQWgd%0A|预订|4f000D253402|D2534|EAY|TNV|EAY|ABV|19:05|20:25|01:20|Y|c5tDeyaRavvl52cxoiVgGILVto5Q82tqfd9I5QQB%2FEikIVOs|20190116|3|Y2|01|04|1|0|||||||无||||有|有|||O0M0O0|OMO|1|0|null\",\"|预订|76000D190602|D1906|ICW|TNV|EAY|ABV|19:49|20:53|01:04|N|TXju8vCbrtxP%2F7vTFBsFMtAaRL4Ku7oZ%2Fl9MupcF8FrJCswk|20190116|3|W2|09|10|1|0|||||||||||无|无|无||O0M090|OM9|0|0|null\",\"%2Fym3bC%2FbYmMqalgyk0Ph5v4zTjgZRZlPMb5gITNG6S4%2FsApnbCctN6wxuLbt2JFeDi4gb%2BUHWPfg%0A1xgRr1%2FR6pGgAz9ekBwKc%2BAYNOI%2BOFuUWr0hF0jqCL2vuj88qaKlxoRT2rkwqUORGkyjYgcHsN5c%0Aod5BpVFm%2Foj2fA9p8R7l5iuXekQBhbgkW3XkJ0EvwT7PjoFPr1GE9%2FLz1y8G7by%2Bi4wyviDPSIiZ%0AGtZQRTQfeNpw07%2FTwyo6toyPlDMaKnMZbgUeMyUZqGfk57agNeZoIXurDmINNJRpy%2BaefJxjBbwb%0AgIIlXA%3D%3D|预订|41000026720F|2672|XAY|DTV|XAY|YNV|22:53|02:37|03:44|Y|Z9rl4ewWisGPzKsq15TjWf1wj3Ek2%2B5gv4Fr%2FL2ukJTWi4%2BZzpayem63a6A%3D|20190116|3|Y2|01|03|0|0||||9|||有||有|有|||||10401030|1413|1|0|null\"]";
        // String testStr = "[\"|预订|4f000D253803|D2538|EAY|TNV|EAY|ABV|10:41|12:07|01:26|N|iPlHSGxzawpH6ea1PLicWCtEVVZK8Nacu0pI5uhG8482ohFp|20190129|3|Y2|01|05|1|0|||||||无||||无|无|||O0M0O0|OMO|1|0|null\",\"|预订|4f000D251206|D2512|EAY|TNV|EAY|ABV|11:08|12:27|01:19|N|iPlHSGxzawpH6ea1PLicWCtEVVZK8Nacu0pI5uhG8482ohFp|20190129|3|Y2|01|04|1|0|||||||无||||无|无|||O0M0O0|OMO|0|0|null\",\"|预订|4f000D251605|D2516|EAY|TNV|EAY|ABV|12:10|13:37|01:27|N|iPlHSGxzawpH6ea1PLicWCtEVVZK8Nacu0pI5uhG8482ohFp|20190129|3|Y2|01|05|1|0|||||||无||||无|无|||O0M0O0|OMO|0|0|null\",\"|预订|4f000D252007|D2520|EAY|TNV|EAY|ABV|13:25|14:37|01:12|N|iPlHSGxzawpH6ea1PLicWCtEVVZK8Nacu0pI5uhG8482ohFp|20190129|3|Y2|01|03|1|0|||||||无||||无|无|||O0M0O0|OMO|0|0|null\",\"|预订|77000D195702|D1960|CXW|TNV|EAY|ABV|15:30|16:48|01:18|N|TXju8vCbrtxP%2F7vTFBsFMtAaRL4Ku7oZ%2Fl9MupcF8FrJCswk|20190129|3|W2|09|12|1|0|||||||||||无|无|无||O0M090|OM9|0|0|null\",\"|预订|4g000D256204|D2562|BBY|TNV|EAY|ABV|16:07|17:39|01:32|N|iPlHSGxzawpH6ea1PLicWCtEVVZK8Nacu0pI5uhG8482ohFp|20190129|3|Y2|04|08|1|0|||||||无||||无|无|||O0M0O0|OMO|1|0|null\",\"|预订|4f000D252807|D2528|EAY|TNV|EAY|ABV|17:25|18:52|01:27|N|iPlHSGxzawpH6ea1PLicWCtEVVZK8Nacu0pI5uhG8482ohFp|20190129|3|Y2|01|05|1|0|||||||无||||无|无|||O0M0O0|OMO|1|0|null\",\"|预订|4f000D253005|D2530|EAY|TNV|EAY|ABV|17:43|19:09|01:26|N|iPlHSGxzawpH6ea1PLicWCtEVVZK8Nacu0pI5uhG8482ohFp|20190129|3|Y2|01|05|1|0|||||||无||||无|无|||O0M0O0|OMO|1|0|null\",\"AXQ4XuYgOBpxRpN9yfpvWi1rBPDRQQPFPSvPeaBGt9gh%2BfwfjB6S%2FZ5%2FL0Y1VEUzJoqP3e3Zkphl%0A8DKIkeza5MDV1PxauyIhQL2oB3CqXSFG3zESuY8VojgDqQl2oqqrC%2F5AVhIzNNaGZRsnByUV9003%0A08hWytkRODI2Nx%2FKsw8u1UIjRZoON7h80nTYxIn%2B1Jx5NvJhTxerb8fGqNzrlEckEFJM4P4iKDmx%0Akmhaj0s9xAw1r%2BJKihq8AXXAVYSNvaLliwdo2Ov5fIEpub%2BN%2B50hThogXW4qJWGXj%2BHRwOAxD3BR%0A3PvR0w%3D%3D|预订|770000K6900C|K690|CUW|TYV|CAY|YNV|19:03|00:23|05:20|Y|%2FPsGYHJUDJ9ey8w3zuKyGc3TbqX%2FXM1j2JIWvk0vYF%2B%2B6sOl1e4FCjgVLS4%3D|20190129|3|W2|12|16|0|0||||1|||无||20|有|||||10401030|1413|1|0|null\"]";
        String bookingAfterTime = "08:00";
        String bookingBeforeTime = "22:00";
        String trainDate = "2019-01-16";
        String fromStation = "西安";
        String toStation = "运城";
        String purposeCode = "ADULT";
        String userSetTrainName = "D2568";
        Map<String, String> trainInfo = trainInfoMap(trainInfoJsonList(bookingAfterTime, bookingBeforeTime, trainDate, fromStation, toStation, purposeCode), userSetTrainName);
        System.out.println(trainInfo);
        // // for (Map<String, String> element: resultListMap(testStr, bookingAfterTime, bookingBeforeTime)){
        // //     System.out.println(element);
        // // }
        // Map<String, String> trainInfo = trainInfoMap(resultListMap(testStr, bookingAfterTime, bookingBeforeTime), "");
        // System.out.println(trainInfo);

    }

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
        Date t1 = new Date(System.currentTimeMillis());
        Map<String, String> resultMap = trainInfoMap(trainInfoJsonList(bookingAfterTime, bookingBeforeTime, trainDate, fromStation, toStation, purposeCode), userSetTrainName);
        Date t2 = new Date(System.currentTimeMillis());
        queryNum++;
        intervalTime = ((double)t2.getTime() - (double)t1.getTime()) / 1000;
        String threadId = Thread.currentThread().getName();
        logger.info(String.format("查询符合条件的列车信息......    线程ID：%1$4s    尝试次数：%2$5d    耗时：%3$5.2f 秒", threadId, queryNum, intervalTime));
        return resultMap;
    }
    /**
     *      获取列车信息，返回一个 List<Map<String, String>>
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
        boolean isMeet = beforeTimeTypeDate.getTime() >= startTimeTypeDate.getTime() && afterTimeTypeDate.getTime() <= startTimeTypeDate.getTime();
        if (isMeet){
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
        return ConvertMap.cityNameToID(cityName);
    }

    /**
     *      计算请求过程耗时
     *
     * @return        耗时
     */
    private static Double sumInterval(){
        double currentTime = System.currentTimeMillis();
        double interval = (currentTime - lastTime) / 1000;
        // 更新时间戳
        lastTime = System.currentTimeMillis();
        return interval;
    }
}

// class CityIdMap{
//     public static Map<String, String> cityIdMap = new HashMap<>();
//     static {
//         String[] cityIdList = Tools.readFileText("./city_id").split("\n");
//         for (String line: cityIdList){
//             String[] cityInfo = line.split("\\|");
//             String cityName = cityInfo[0];
//             String cityId = cityInfo[1];
//             cityIdMap.put(cityName, cityId);
//         }
//     }
//
//     public static void main(String[] args) {
//         System.out.println(CityIdMap.cityIdMap);
//         System.out.println(CityIdMap.cityIdMap.get("海口东北"));
//     }
// }