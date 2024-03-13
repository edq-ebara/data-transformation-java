import java.util.List;

/**
 * Json转换信息
 */
public class JsonTranferInfo {

    private String orgTemplate;
    private String aimTemplate;
    private List<JsonMapping> jsonMappings ;


    public String getOrgTemplate() {
        return orgTemplate;
    }

    public void setOrgTemplate(String orgTemplate) {
        this.orgTemplate = orgTemplate;
    }

    public String getAimTemplate() {
        return aimTemplate;
    }

    public void setAimTemplate(String aimTemplate) {
        this.aimTemplate = aimTemplate;
    }

    public List<JsonMapping>  getJsonMappings() {
        return jsonMappings;
    }

    public void setJsonMappings(List<JsonMapping>  jsonMappings) {
        this.jsonMappings = jsonMappings;
    }

    public JsonTranferInfo(String orgTemplate, String aimTemplate, List<JsonMapping> jsonMappings) {
        this.orgTemplate = orgTemplate;
        this.aimTemplate = aimTemplate;
        this.jsonMappings = jsonMappings;
    }
}
