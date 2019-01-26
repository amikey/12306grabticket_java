import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

    private static Map<String, String> seatNameToNumber;
    private static Map<String, String> seatNumberToName;
    private static Map<String, String> cityIdMap;

    static {
        setCityIdMap();
        setSeatNameToNumber();
        setSeatNumberToName();
    }

    /**
     *  初始化城市ID转换表
     */
    private static void setCityIdMap(){
        String[] cityIdList = Tools.readFileText("./city_id").split("\n");
        cityIdMap = new HashMap<>();
        for (String line: cityIdList){
            String[] cityInfo = line.split("\\|");
            String cityName = cityInfo[0];
            String cityId = cityInfo[1];
            cityIdMap.put(cityName, cityId);
        }
    }
    /**
     *  初始化 座位名 到 ID 转换表
     */
    private static void setSeatNameToNumber(){
        seatNameToNumber = new HashMap<>();
        seatNameToNumber.put("商务座", "9");
        seatNameToNumber.put("一等座", "M");
        seatNameToNumber.put("二等座", "O");
        seatNameToNumber.put("无座", "WZ");
        seatNameToNumber.put("硬座", "A1");
        seatNameToNumber.put("硬卧", "A3");
        seatNameToNumber.put("软卧", "A4");
        seatNameToNumber.put("高级软卧", "A6");
    }
    /**
     *  初始化 ID 到 座位名 转换表
     */
    private static void setSeatNumberToName(){
        seatNumberToName = new HashMap<>();
        seatNumberToName.put("9", "商务座");
        seatNumberToName.put("M", "一等座");
        seatNumberToName.put("O", "二等座");
        seatNumberToName.put("WZ", "无座");
        seatNumberToName.put("A1", "硬座");
        seatNumberToName.put("A3", "硬卧");
        seatNumberToName.put("A4", "软卧");
        seatNumberToName.put("A6", "高级软卧");
    }

    /**
     *      输入城市名返回城市ID
     *
     * @param cityName  城市名
     * @return          城市ID
     */
    public static String cityNameToID(String cityName){
        return cityIdMap.get(cityName);
    }
    /**
     *      座位名--> 座位号
     *
     * @param seatName  座位类型名
     * @return          对应编号
     */
    public static String seatNameToNumber(String seatName){
        return seatNameToNumber.get(seatName);
    }
    /**
     *      座位号 --> 座位名
     *
     * @param seatNumber    对应编号
     * @return              座位类型名
     */
    public static String seatNumberToName(String seatNumber){
        return seatNumberToName.get(seatNumber);
    }

    /**
     *      转换一个String[] 从 seatName 到 seatNumber
     * @param seatNameArr      seatNameArr
     * @return                 seatNumberArr
     */
    public static String[] convSeatNameToNumber(String[] seatNameArr){
        List<String> seatNumberList = new ArrayList<>();
        for (String seatName: seatNameArr){
            seatNumberList.add(seatNameToNumber(seatName));
        }
        return seatNumberList.toArray(new String[0]);
    }
}
