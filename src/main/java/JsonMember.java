import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Json成员
 */
public class JsonMember {


    private String name;


    private String type;


    private JsonNode value;


    private String path;

    @JsonIgnore
    private JsonMember parent;

    private JsonNode jsonNode;


    private List<JsonMember> children = new ArrayList<>();


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }


    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }


    public JsonNode getValue() {
        return value;
    }

    public void setValue(JsonNode value) {
        this.value = value;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public JsonMember getParent() {
        return parent;
    }

    public void setParent(JsonMember parent) {
        this.parent = parent;
    }


    public List<JsonMember> getChildren() {
        return children;
    }

    public void setChildren(List<JsonMember> children) {
        this.children = children;
    }


    public JsonNode getJsonNode() {
        return jsonNode;
    }

    public void setJsonNode(JsonNode jsonNode) {
        this.jsonNode = jsonNode;
    }

//
//    public JsonMember cloneDeep() {
//        JsonMember newJsonMember = new JsonMember();
//        newJsonMember.setName(this.getName());
//        newJsonMember.setPath(this.getPath());
//        newJsonMember.setParent(this.getParent());
//        newJsonMember.setType(this.getType());
//        newJsonMember.setJsonNode(this.getJsonNode());
//        newJsonMember.setChildren(this.getChildren());
//        newJsonMember.setValue(this.getValue());
//        return newJsonMember;
//    }
}
