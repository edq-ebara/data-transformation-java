import java.util.ArrayList;
import java.util.List;

/**
 * 对象转换对象
 */
public class Main_Object_Object {

    public static void main(String[] args) {


        String orgJson = "{\"Name_Org\":\"JZM\",\"Age_Org\":\"18\"}"; // JSON字符串
        String aimJson = "{\"Name_Aim\":\"\",\"Age_Aim\":\"\"}"; // JSON字符串

        /// 1：源Key->目标Key
        /// 2：源Key->目标Value
        /// 3：源Value->目标Key
        /// 4：源Value->目标Value
        List<JsonMapping> jsonMappings = new ArrayList<>();
        jsonMappings.add(new JsonMapping("root.Name_Aim", "root.Name_Org", 3));
        jsonMappings.add(new JsonMapping("root.Age_Aim", "root.Age_Org", 3));
        jsonMappings.add(new JsonMapping("root.Name_Aim", "root.Name_Org", 4));
        jsonMappings.add(new JsonMapping("root.Age_Aim", "root.Age_Org", 4));


        JsonTranferUtil jsonTranferUtil = null;
        try {
            jsonTranferUtil = new JsonTranferUtil(orgJson,aimJson,jsonMappings);
        } catch (Exception e) {
            e.printStackTrace();
        }
        String tranJsonResult=jsonTranferUtil.tranJson();

        System.out.println("******************结果 **********************");

        System.out.println("The Result:" + tranJsonResult);




    }
}
