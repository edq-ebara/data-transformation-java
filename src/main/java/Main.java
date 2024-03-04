import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) {

        String orgJson = "{\"Tid\":1709192119,\"Values\":[{\"Value\":{\"quality\":\"GOOD\",\"tid\":1709192048,\"timeStamp\":\"2024-02-29T07:34:08.966607900Z\",\"value\":3333},\"address\":\"2\",\"coefficient\":0.0,\"contextCode\":\"\",\"dataFormat\":\"\",\"dataLayout\":\"\",\"dataType\":\"\",\"driverName\":\"\",\"id\":\"\",\"key\":\"\",\"maxValue\":\"9999\",\"minValue\":\"0\",\"offset\":0.0,\"sort\":0,\"stationNo\":\"dev\",\"switchKey\":\"\",\"tagCode\":\"IOTMqttDriver_AI00004\",\"tagName\":\"\",\"tagState\":\"\",\"troubleType\":\"\",\"unit\":\"\"},{\"Value\":{\"quality\":\"GOOD\",\"tid\":1709192048,\"timeStamp\":\"2024-02-29T07:34:08.964608500Z\",\"value\":2222},\"address\":\"1\",\"coefficient\":0.0,\"contextCode\":\"\",\"dataFormat\":\"\",\"dataLayout\":\"\",\"dataType\":\"\",\"driverName\":\"\",\"id\":\"\",\"key\":\"\",\"maxValue\":\"9999\",\"minValue\":\"0\",\"offset\":0.0,\"sort\":0,\"stationNo\":\"01030203_2003\",\"switchKey\":\"\",\"tagCode\":\"IOTMqttDriver_AI00002\",\"tagName\":\"\",\"tagState\":\"\",\"troubleType\":\"\",\"unit\":\"\"}]}"; // JSON字符串
        String aimJson = "{\"dev\":{\"6327c0c4\":{\"a\":\"0\"}},\"time\":1000000000}"; // JSON字符串


        List<JsonMapping> jsonMappings = new ArrayList<>();
        jsonMappings.add(new JsonMapping("root.time", "root.Tid", 4));
        jsonMappings.add(new JsonMapping("root.dev.6327c0c4", "root.Values[1].stationNo", 3));
        jsonMappings.add(new JsonMapping("root.dev.6327c0c4", "root.Values", 4));
        jsonMappings.add(new JsonMapping("root.dev.6327c0c4.a", "root.Values[*].address", 3));
        jsonMappings.add(new JsonMapping("root.dev.6327c0c4.a", "root.Values[*].Value.value", 4));


        String resultStr = new JsonTranferUtils().tranJson(orgJson, aimJson, jsonMappings);

        System.out.println("******************结果 **********************");

        System.out.println("The Result:" + resultStr);




    }
}
