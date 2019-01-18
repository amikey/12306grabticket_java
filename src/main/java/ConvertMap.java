import java.util.HashMap;
import java.util.Map;

/**
 * @program: GrabTicket
 * @description: 这个类包含了一些常用的数据转换，例如：二等座 --> O
 * @author: Wen lyuzhao
 * @create: 2019-01-18 14:03
 *
 *      该类全是静态方法
 *
 **/
public class ConvertMap {

    /**
     *      输入座位类型名返回对应编号
     *
     * @param seatName  座位类型名
     * @return          对应编号
     */
    public static String seatNameToNumber(String seatName){
        Map<String, String> seatMap = new HashMap<>();
        seatMap.put("商务座", "A9");
        seatMap.put("一等座", "M");
        seatMap.put("二等座", "O");
        seatMap.put("无座", "WZ");
        seatMap.put("硬座", "A1");
        seatMap.put("硬卧", "A3");
        seatMap.put("软卧", "A4");
        seatMap.put("高级软卧", "A6");
        return seatMap.get(seatName);
    }
}
