import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class Main_HaiLuo {

    public static void main(String[] args) {

        ObjectMapper mapper = new ObjectMapper();


        String fileName = "E:\\2.txt";
        StringBuilder content = new StringBuilder();
        try {
            BufferedReader br = null;
            try {
                br = new BufferedReader(new FileReader(fileName));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            String line;
            while ((line = br.readLine()) != null) {
                content.append(line);
            }
        } catch (IOException e) {
            System.err.println("Error reading the file: " + e.getMessage());
        }


        String orgJson = content.toString().replaceAll(" ", "");
        orgJson = orgJson.substring(0, orgJson.length());
//        String aimJson = "{\"dev\":{\"devcode\":{\"tag\":\"value\"}},\"time\":166355111}"; // JSON字符串
        String aimJson = "{\"Tid\":1709715147,\"Values\":[{\"StationNo\":\"dev\",\"Values\":[{\"Value\":{\"quality\":\"GOOD\",\"timeStamp\":\"2024-03-06T08:52:25.792752300Z\",\"value\":9220},\"address\":\"2\",\"coefficient\":0.0,\"contextCode\":\"\",\"dataFormat\":\"\",\"dataLayout\":\"\",\"dataType\":\"\",\"driverName\":\"\",\"id\":\"\",\"key\":\"\",\"maxValue\":\"9999\",\"minValue\":\"0\",\"offset\":0.0,\"sort\":0,\"stationNo\":\"dev\",\"switchKey\":\"\",\"tagCode\":\"IOTMqttDriver_AI00001\",\"tagName\":\"\",\"tagState\":\"\",\"troubleType\":\"\",\"unit\":\"\"}]}]}"; // JSON字符串

        //海螺=》御控
//        List<JsonMapping> jsonMappings = new ArrayList<>();
//        jsonMappings.add(new JsonMapping("root.time", "root.Tid", 4,new JsonMappingOptions(1,2,1,1)));
//        jsonMappings.add(new JsonMapping("root.dev.devcode", "root.Values[0].StationNo", 3,new JsonMappingOptions(1,1,1,1)));
//        jsonMappings.add(new JsonMapping("root.dev.*", "root.Values[0].Values", 4,new JsonMappingOptions(1,2,3,1)));
//        jsonMappings.add(new JsonMapping("root.dev.*.*", "root.Values[0].Values[*].address", 3,new JsonMappingOptions(1,1,1,2)));
//        jsonMappings.add(new JsonMapping("root.dev.*.*", "root.Values[0].Values[*].Value.value", 4,new JsonMappingOptions(1,1,1,2)));
        //御控=》海螺
        List<JsonMapping> jsonMappings = new ArrayList<>();
        jsonMappings.add(new JsonMapping("root.Tid", "root.time", 4, new JsonMappingOptions(1, 2, 1, 1)));
        jsonMappings.add(new JsonMapping("root.Values[*].StationNo", "root.dev.station", 2, new JsonMappingOptions(1, 1, 1, 1)));
        jsonMappings.add(new JsonMapping("root.Values[0].Values", "root.dev.*", 4, new JsonMappingOptions(1, 2, 3, 1)));
        jsonMappings.add(new JsonMapping("root.Values[0].Values[*].address", "root.dev.*.*", 2, new JsonMappingOptions(1, 1, 1, 2)));
        jsonMappings.add(new JsonMapping("root.Values[0].Values[*].Value.value", "root.dev.*.*", 4,new JsonMappingOptions(1,1,1,2)));

        JsonTranferUtil jsonTranferUtil = null;
        String result = "";
        String resultError = "";

        try {
            jsonTranferUtil = new JsonTranferUtil(orgJson, aimJson, jsonMappings);
            result = jsonTranferUtil.tranJson();
            resultError = mapper.writeValueAsString(jsonTranferUtil.getTranErrors());
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("******************结果 **********************");

        System.out.println(result);
        System.out.println(resultError);
    }
}
