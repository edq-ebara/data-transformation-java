import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Json 数据转换
 */
public class JsonTranferUtil {

    //region 成员属性变量

    ObjectMapper mapper = new ObjectMapper();
    private String Json_Path_Regex = "^[\\d\\w][\\d\\w\\_]*((\\[([\\w\\d\\_]+|\\*)\\])*|((\\.\\*)|(\\.[\\d\\w][\\d\\w\\_]*))*)*$";

    private String orgTemplate;
    private String aimTemplate;
    private List<JsonMapping> jsonMappings;

    private JsonNode jOrg = null;// 将JSON字符串转换为JsonNode对象
    private JsonNode jAim = null; // 将JSON字符串转换为JsonNode对象

    private List<String> tranLogs = new ArrayList<>();
    private List<String> tranErrors = new ArrayList<>();

    //目标是否替换
    private List<String> replaceAimArr = new ArrayList<>();

    //endregion

    //region 成员构造函数

    public JsonTranferUtil(String orgTemplate, String aimTemplate, List<JsonMapping> jsonMappings) throws Exception {
        if (orgTemplate == "" || orgTemplate == null || aimTemplate == "" || aimTemplate == null || jsonMappings == null || jsonMappings.size() < 0) {
            throw new Exception("源模板、目标模板、映射关系不能为空！");
        }

        this.orgTemplate = orgTemplate;
        this.aimTemplate = aimTemplate;


//        jsonMappings.sort((a, b) -> {
//
//            if (a.getAimJsonPath() == b.getAimJsonPath()) {
//                if (a.getTranType() != b.getTranType()) {
//                    if (a.getTranType() == 1 || a.getTranType() == 3) {
//                        return -1;
//                    }
//                    if (b.getTranType() == 1 || b.getTranType() == 3) {
//                        return 1;
//                    }
//                    return a.getTranType() - b.getTranType();
//                } else if (a.getOrgJsonPath() != b.getOrgJsonPath()) {
//                    return a.getOrgJsonPath().compareTo(b.getOrgJsonPath());
//                }
//            } else if (a.getOrgJsonPath() != b.getOrgJsonPath()) {
//                return a.getOrgJsonPath().compareTo(b.getOrgJsonPath());
//            }
//
//            return 0;
//        });

        this.jsonMappings = mapper.readValue(mapper.writeValueAsString(jsonMappings), mapper.getTypeFactory().constructCollectionType(List.class, JsonMapping.class));


    }


    //endregion


    //region 成员私有方法


    /**
     * 依据规则展开Mapping
     *
     * @param type 1:源路径 2：目标路径
     */
    private void expandMappingForPatten(JsonMember jAimRoot, JsonMember jOrgRoot, List<JsonMapping> mappings, JsonMapping mapping, int type) throws Exception {

        if (mapping.getOptions() != null && mapping.getOptions().getTranWay() == 2) {
            this.expandMappingForPatten_OneToOne(jAimRoot, jOrgRoot, mappings, mapping);
        } else {
            String path = type == 1 ? mapping.getOrgJsonPath() : mapping.getAimJsonPath();
            JsonMember jRoot = type == 1 ? jOrgRoot : jAimRoot;
            String curPath = "";
            if (path.indexOf("*") > 0) {
                if (path.indexOf(".*") == (path.indexOf("*") - 1)) { //对象路径
                    curPath = path.substring(0, path.indexOf(".*"));
                    JsonMember jcur = this.getElementByPath(jRoot, curPath);
                    int mappingIndex = mappings.indexOf(mapping);
                    //删除原映射
                    mappings.remove(mappingIndex);
                    if (jcur != null) {

                        int keyIndex = 0;


                        for (JsonMember child : jcur.getChildren()) {
                            String newPath = path.replace(curPath + ".*", curPath + "." + child.getName());
                            JsonMapping newMapping = new JsonMapping(type == 2 ? newPath : mapping.getAimJsonPath()
                                    , type == 1 ? newPath : mapping.getOrgJsonPath()
                                    , mapping.getTranType()
                                    , mapping.getOptions()
                            );
                            //添加新映射
                            mappings.add(mappingIndex + keyIndex, newMapping);

                            if (newPath.indexOf("*") > 0) {
                                this.expandMappingForPatten(jAimRoot, jOrgRoot, mappings, newMapping, type);
                            }
                            keyIndex++;
                        }
                    }

                } else if (path.indexOf("[*") == (path.indexOf("*") - 1)) {//数组路径
                    curPath = path.substring(0, path.indexOf("[*"));
                    int mappingIndex = mappings.indexOf(mapping);
                    //删除原映射
                    mappings.remove(mappingIndex);
                    JsonMember jcur = this.getElementByPath(jRoot, curPath);
                    if (jcur != null) {

                        for (int index = 0; index < jcur.getChildren().size(); index++) {
                            String newPath = path.replace(curPath + "[*]", curPath + "[" + index + "]");
                            JsonMapping newMapping = new JsonMapping(type == 2 ? newPath : mapping.getAimJsonPath()
                                    , type == 1 ? newPath : mapping.getOrgJsonPath()
                                    , mapping.getTranType()
                                    , mapping.getOptions()
                            );
                            //添加新映射
                            mappings.add(mappingIndex + index, newMapping);
                            if (newPath.indexOf("*") > 0) {
                                this.expandMappingForPatten(jAimRoot, jOrgRoot, mappings, newMapping, type);
                            }
                        }
                    }
                } else {
                    throw new Exception("无法分析出是什么路径！");
                }
            }
        }
    }

    /**
     * 依据规则展开Mapping（一对一映射）
     *
     * @param mapping
     */
    private void expandMappingForPatten_OneToOne(JsonMember jAimRoot, JsonMember jOrgRoot, List<JsonMapping> mappings, JsonMapping mapping) {


        //针对一对一映射，先处理当前映射的目标路径的 1 key-key 或者3 value-key，后处理当前映射的目标路径的 2 key-value 或者 4 value-value
        List<JsonMapping> mappings_1_3 = new ArrayList<>();

        mappings_1_3 = mappings.stream().filter((item) -> {
            if ((item.getTranType() == 1 || item.getTranType() == 3) && item.getAimJsonPath() == mapping.getAimJsonPath()) {
                return true;
            }
            return false;
        }).collect(Collectors.toList());

        mappings_1_3.forEach(item -> {
            List<JsonMember> jcurAimArr_1_3 = new ArrayList<>();
            List<JsonMember> jcurOrgArr_1_3 = new ArrayList<>();
            try {
                this.getElementByPathWithPatten(jAimRoot, item.getAimJsonPath(), jcurAimArr_1_3);
                this.getElementByPathWithPatten(jOrgRoot, item.getOrgJsonPath(), jcurOrgArr_1_3);
            } catch (Exception e) {
                e.printStackTrace();
            }
            int mappingIndex_1_3 = mappings.indexOf(item);

            for (int index = 0; index < jcurOrgArr_1_3.size(); index++) {
                if (jcurAimArr_1_3.size() > index && jcurAimArr_1_3.get(index) != null) {
                    JsonMapping newMapping = new JsonMapping(jcurAimArr_1_3.get(index).getPath()
                            , jcurOrgArr_1_3.get(index).getPath()
                            , item.getTranType()
                            , item.getOptions()
                    );
                    //添加新映射
                    mappings.add(mappingIndex_1_3 + index, newMapping);
                }
            }
            //删除原映射
            mappings.remove(mappings.indexOf(item));
        });


        List<JsonMapping> mappings_2_4 = new ArrayList<>();
        //针对一对一映射，先处理当前映射的目标路径的 1 key-key 或者3 value-key，后处理当前映射的目标路径的 2 key-value 或者 4 value-value
        mappings_2_4 = mappings.stream().filter(item -> {
            if ((item.getTranType() == 2 || item.getTranType() == 4) && item.getAimJsonPath() == mapping.getAimJsonPath()) {
                return true;
            }
            return false;
        }).collect(Collectors.toList());

        mappings_2_4.forEach(item -> {

            List<JsonMember> jcurAimArr_2_4 = new ArrayList<>();
            List<JsonMember> jcurOrgArr_2_4 = new ArrayList<>();
            try {
                this.getElementByPathWithPatten(jAimRoot, item.getAimJsonPath(), jcurAimArr_2_4);
                this.getElementByPathWithPatten(jOrgRoot, item.getOrgJsonPath(), jcurOrgArr_2_4);
            } catch (Exception e) {
                e.printStackTrace();
            }

            int mappingIndex_2_4 = mappings.indexOf(item);
            for (int index = 0; index < jcurOrgArr_2_4.size(); index++) {

                if (jcurAimArr_2_4.size() > index && jcurAimArr_2_4.get(index) != null) {
                    JsonMapping newMapping = new JsonMapping(jcurAimArr_2_4.get(index).getPath()
                            , jcurOrgArr_2_4.get(index).getPath()
                            , item.getTranType()
                            , item.getOptions()
                    );
                    //添加新映射
                    mappings.add(mappingIndex_2_4 + index, newMapping);
                }
            }

            //删除原映射
            mappings.remove(mappings.indexOf(item));

        });

    }

    /**
     * 替换最后一个匹配的字符串
     *
     * @param orgStr
     * @param replaceStr
     */
    private String replaceLastStrs(String orgStr, String replaceStr, int objType) {


        Pattern regexArray = Pattern.compile("^.*(?=\\[.*?\\])");
        Pattern regexObject = Pattern.compile("^.*(?=\\.\\w*)");

        String replaceTemp = "";
        String newStr = "";
        List<String> regStrs = new ArrayList<>();
        Matcher matcher = null;
        if (objType == 1) {//数组

            matcher = regexArray.matcher(orgStr);
            while (matcher.find()) {
                regStrs.add(matcher.group());
            }
            if (regStrs != null && regStrs.size() > 0) {
                newStr = regStrs.get(0);
            } else {
                newStr = orgStr;
            }
            return newStr + replaceStr;
        } else {//对象
            if (orgStr.indexOf(".") >= 0) {
                matcher = regexObject.matcher(orgStr);
                while (matcher.find()) {
                    regStrs.add(matcher.group());
                }
                if (regStrs != null && regStrs.size() > 0) {
                    newStr = regStrs.get(0);
                } else {
                    newStr = orgStr;
                }
                return newStr + "." + replaceStr;
            } else {
                return replaceStr;
            }
        }


    }

    /**
     * 依据路径获取元素
     */
    private JsonMember getElementByPath(JsonMember jRoot, String path) {
        return JsonMember.getJsonMemeberByPath(jRoot, path);
    }

    /**
     * 依据路径获取元素(依据规则)
     */
    private void getElementByPathWithPatten(JsonMember jRoot, String path, List<JsonMember> eleArr) throws Exception {
        String curPath = "";
        if (path.indexOf("*") > 0) {

            if (path.indexOf(".*") == (path.indexOf("*") - 1)) { //对象路径
                curPath = path.substring(0, path.indexOf(".*"));
                JsonMember jcur = this.getElementByPath(jRoot, curPath);
                if (jcur != null) {
                    for (JsonMember child : jcur.getChildren()) {
                        String newPath = path.replace(curPath + ".*", curPath + "." + child.getName());

                        if (newPath.indexOf("*") > 0) {
                            this.getElementByPathWithPatten(jRoot, newPath, eleArr);
                        } else {
                            JsonMember jcurChild = this.getElementByPath(jRoot, newPath);
                            eleArr.add(jcurChild);
                        }
                    }
                }

            } else if (path.indexOf("[*") == (path.indexOf("*") - 1)) {//数组路径
                curPath = path.substring(0, path.indexOf("[*"));
                JsonMember jcur = this.getElementByPath(jRoot, curPath);
                if (jcur != null) {

                    for (int index = 0; index < jcur.getChildren().size(); index++) {
                        String newPath = path.replace(curPath + "[*]", curPath + "[" + index + "]");

                        if (newPath.indexOf("*") > 0) {
                            this.getElementByPathWithPatten(jRoot, newPath, eleArr);
                        } else {
                            JsonMember jcurChild = this.getElementByPath(jRoot, newPath);
                            eleArr.add(jcurChild);
                        }
                    }
                }
            } else {
                throw new Exception("无法分析出是什么路径！");
            }

        } else {
            JsonMember jcur = this.getElementByPath(jRoot, path);
            eleArr.add(jcur);
        }

    }

    /**
     * 压缩JSON
     *
     * @param obj
     * @param currentPath
     * @param paths
     * @returns
     */
    private JsonNode compressJson(JsonNode obj, String currentPath, List<String> paths) {

        if (obj == null || !obj.isObject() && !obj.isArray()) {
            return obj;
        }

        if (obj.isArray()) {
            ArrayNode jsonNode = (ArrayNode) obj;
            Iterator<JsonNode> children = obj.elements();
            List<JsonNode> temJsonNodeList = new ArrayList<>();
            if (children != null && children.hasNext()) {
                Integer index = 0;
                while (children.hasNext()) {
                    temJsonNodeList.add(compressJson(children.next(), currentPath + "[" + index.toString() + "]", paths));
                    index++;
                }
                jsonNode.removeAll();
                temJsonNodeList.forEach(item -> {
                    jsonNode.add(item);
                });

            }
            return jsonNode;
        }


        Iterator<Map.Entry<String, JsonNode>> children = obj.fields();
        if (children != null && children.hasNext()) {
            List<Map.Entry<String, JsonNode>> temJsonNodeList = new ArrayList<>();
            children.forEachRemaining(child -> {
                String newPath = currentPath != null && currentPath != "" ? currentPath + "." + child.getKey() : child.getKey();
                if (paths.stream().filter(p -> p.contains(newPath)).findFirst().isPresent()) {
                    temJsonNodeList.add(new AbstractMap.SimpleEntry<>(child.getKey(), this.compressJson(child.getValue(), newPath, paths)));
                }
            });
            ((ObjectNode) obj).removeAll();
            temJsonNodeList.forEach(item -> {
                ((ObjectNode) obj).set(item.getKey(), item.getValue());
            });
        }
        return obj;
    }

    /**
     * 获取动态变量的值
     */
    private String getVaraibleValue(String var_type) {

        switch (var_type) {
            case "#Time#":

                // 创建一个SimpleDateFormat对象，指定UTC时间格式并设置时区为UTC
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
                sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                // 创建一个Date对象，表示当前时间
                Date currentDate = new Date();
                // 如果需要将UTC时间戳转换为UTC时间字符串，可以使用toUTCString()方法
                String utcTimeString = sdf.format(currentDate);

                return utcTimeString;
            case "#Time_L#":

                // 创建一个Date对象，表示当前时间
                Date currentDate1 = new Date();

                // 获取当前时间的UTC时间戳（毫秒）
                long utcTimestamp = currentDate1.getTime();

                return String.valueOf(utcTimestamp);
            default:
                break;
        }
        return null;
    }

    /// <summary>
    /// 是否是有效的映射路径
    /// type 1：源路径  2：目标路径
    /// 1、判断是否是变量路径
    /// </summary>
    /// <param name="mapping"></param>
    /// <returns></returns>
    private boolean isValidMapping(JsonMember jOrg, JsonMember jAim, JsonMapping mapping, int type)
    {
        if (type == 1)
        {
            JsonMember jAimCur = this.getElementByPath(jAim, mapping.getAimJsonPath());
            String jAimCurType = jAimCur.getType().toUpperCase();
            Boolean result = Pattern.compile("^#.*#$").matcher(mapping.getOrgJsonPath()).matches();
            if (result && mapping.getTranType()==4&& jAimCurType!="OBJECT"&&jAimCurType!="ARRAY")
            {
                return true;
            }
        }
        return false;
    }


    /**
     * 获取Json成员
     *
     * @param jsonNode
     */
    private JsonMember getJsonMember(JsonNode jsonNode, String jsonName, JsonMember parent) {
        JsonMember jsonMember = new JsonMember();
        jsonMember.setName(jsonName);
        jsonMember.setParent(parent);
        jsonMember.setType(String.valueOf(jsonNode.getNodeType()));

        String path = jsonName;
        if (parent != null) {
            String arrayRegex = "^\\[\\d+\\]$";
            // 编译正则表达式/创建 Matcher 对象
            Matcher matcher = Pattern.compile(arrayRegex).matcher(path);
            // 检查输入的城市名称是否符合正则表达式规则
            if (matcher.matches()) {
                path = parent.getPath() + path;
            } else {
                path = parent.getPath() + "." + path;
            }
        }
        jsonMember.setPath(path);
        if (parent != null) {
            parent.getChildren().add(jsonMember);
        }


        //递归子节点
        if (jsonNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> children = jsonNode.fields();
            if (children != null && children.hasNext()) {
                children.forEachRemaining(child -> {
                    getJsonMember(child.getValue(), child.getKey(), jsonMember);

                });
            }
        } else if (jsonNode.isArray()) {
            Iterator<JsonNode> children = jsonNode.elements();
            if (children != null && children.hasNext()) {
                Integer index = 0;
                while (children.hasNext()) {
                    getJsonMember(children.next(), "[" + index.toString() + "]", jsonMember);
                    index++;
                }

            }

        }

        return jsonMember;


    }

    /**
     * Json数据转换
     */
    private void tranJson_Inner(JsonMember jOrgMember, JsonMember jAimMember, List<JsonMapping> mappings, JsonMapping mapping) throws Exception {
        JsonMember jOrgCur = this.getElementByPath(jOrgMember, mapping.getOrgJsonPath());
        JsonMember jAimCur = this.getElementByPath(jAimMember, mapping.getAimJsonPath());

        if (jOrgCur == null&&!isValidMapping(jOrgMember,jAimMember,mapping,1) || jAimCur == null) {
            if (jOrgCur == null ) {
                this.tranErrors.add(mapper.writeValueAsString(mapping) + "源路径定位不到属性！");

            }
            if (jAimCur == null) {
                this.tranErrors.add(mapper.writeValueAsString(mapping) + "目标路径定位不到属性！");

            }
            //转换完成则删掉当前映射
            mappings.remove(mappings.indexOf(mapping));
            return;
        }
        String jOrgCurType = jOrgCur != null ? jOrgCur.getType().toUpperCase() : "other";
        String jAimCurType = jAimCur.getType().toUpperCase();
        switch (jAimCurType) {
            case "OBJECT"://目标属性是个对象
                switch (jOrgCurType) {
                    case "OBJECT"://源属性是个对象
                        this.tran_Object_Object(jOrgMember, jAimMember, mappings, mapping);
                        break;
                    case "ARRAY"://源属性是个数组
                        this.tran_Array_Object(jOrgMember, jAimMember, mappings, mapping);
                        break;
                    case "STRING":
                    case "NUMBER":
                    case "BOOLEAN":
                    default:
                        //源属性是个基础类型
                        this.tran_Base_Object(jOrgMember, jAimMember, mappings, mapping);
                }

                break;
            case "ARRAY"://目标属性是个数组
                switch (jOrgCurType) {
                    case "OBJECT"://源属性是个对象
                        this.tran_Object_Array(jOrgMember, jAimMember, mappings, mapping);
                        break;
                    case "ARRAY"://源属性是个数组
                        this.tran_Array_Array(jOrgMember, jAimMember, mappings, mapping);
                        break;
                    case "STRING":
                    case "NUMBER":
                    case "BOOLEAN":
                    default:
                        //源属性是个基础类型
                        this.tran_Base_Array(jOrgMember, jAimMember, mappings, mapping);
                }
                break;
            case "STRING":
            case "NUMBER":
            case "BOOLEAN":
            default:
                //目标属性是个基础类型
                switch (jOrgCurType) {
                    case "ARRAY": //源属性是个数组
                        this.tran_Array_Base(jOrgMember, jAimMember, mappings, mapping);
                        break;
                    case "OBJECT"://源属性是个对象
                        this.tran_Object_Base(jOrgMember, jAimMember, mappings, mapping);
                        break;
                    case "STRING":
                    case "NUMBER":
                    case "BOOLEAN":
                    default:
                        //源属性是个基础类型
                        this.tran_Base_Base(jOrgMember, jAimMember, mappings, mapping);
                }
        }

    }

    /**
     * 转换：数组=》数组
     */
    private void tran_Array_Array(JsonMember jOrgMember, JsonMember jAimMember, List<JsonMapping> mappings, JsonMapping mapping) throws Exception {

        JsonMember jAimCur = null;
        String jAimCurPath = null;
        String jAimCurKey = null;
        JsonNode jAimCurNode = null;

        JsonNode jAimCurChild_Node = null;
        JsonMember jAimCurChild = null;


        JsonMember jOrgCur = null;
        String jOrgCurPath = null;
        String jOrgCurKey = null;
        JsonNode jOrgCurNode = null;

        JsonNode jOrgCurChild_Node = null;
        JsonMember jOrgCurChild = null;


        //1：源Key->目标Key  2：源Key->目标Value  3：源Value->目标Key 4：源Value->目标Value
        switch (mapping.getTranType()) {
            case 1:
                this.tran_Key_Key(jOrgMember, jAimMember, mappings, mapping);
                break;
            case 2:
                jAimCur = this.getElementByPath(jAimMember, mapping.getAimJsonPath());
                jOrgCur = this.getElementByPath(jOrgMember, mapping.getOrgJsonPath());

                if (jOrgCur == null || jAimCur == null) {
                    if (jOrgCur == null) {
                        this.tranErrors.add(mapper.writeValueAsString(mapping) + "源路径定位不到属性！");
                    }
                    if (jAimCur == null) {
                        this.tranErrors.add(mapper.writeValueAsString(mapping) + "目标路径定位不到属性！");
                    }

                    //转换完成则删掉当前映射
                    mappings.remove(mappings.indexOf(mapping));
                    return;
                }


                jAimCurPath = mapping.getAimJsonPath();
                jAimCurKey = jAimCur.getName();
                jAimCurNode = JsonMember.getJsonNodeByPath(this.jAim, mapping.getAimJsonPath());


                jOrgCurPath = mapping.getOrgJsonPath();
                jOrgCurKey = jOrgCur.getName();
                jOrgCurNode = JsonMember.getJsonNodeByPath(this.jOrg, mapping.getOrgJsonPath());


                /*******************执行转换******************* */


                if (mapping.getOptions() != null) {
                    //1:追加新元素到数组/对象 2：替换数组/对象的原有属性  默认为1
                    if (mapping.getOptions().getAddElementsOption() == 2) {
                        if (!this.replaceAimArr.stream().filter(path -> path.equals(mapping.getAimJsonPath())).findFirst().isPresent()) {
                            //清空jAimCur的子元素
                            jAimCur.getChildren().clear();
                            //清空jAimCur关联的Node的子Node
                            ((ArrayNode) jAimCurNode).removeAll();

                            this.replaceAimArr.add(mapping.getAimJsonPath());
                        }
                    }
                }

                //创建jAimCur关联的Node的子Node
                jAimCurChild_Node = JsonNodeFactory.instance.textNode(jOrgCurKey);
                //添加子Node到jAimCur关联的Node
                ((ArrayNode) jAimCurNode).add(jAimCurChild_Node);

                //创建jAimCur的子元素并添加到jAimCur
                jAimCurChild = this.getJsonMember(jAimCurChild_Node, "[" + (jAimCur.getChildren().size()) + "]", jAimCur);


                //转换完成则删掉当前映射
                mappings.remove(mappings.indexOf(mapping));


                break;
            case 3:
                this.tran_Value_Key(jOrgMember, jAimMember, mappings, mapping);
                break;
            case 4:


                jAimCur = this.getElementByPath(jAimMember, mapping.getAimJsonPath());
                jOrgCur = this.getElementByPath(jOrgMember, mapping.getOrgJsonPath());

                if (jOrgCur == null || jAimCur == null) {
                    if (jOrgCur == null) {

                        this.tranErrors.add(mapper.writeValueAsString(mapping) + "源路径定位不到属性！");

                    }
                    if (jAimCur == null) {
                        this.tranErrors.add(mapper.writeValueAsString(mapping) + "目标路径定位不到属性！");

                    }

                    //转换完成则删掉当前映射
                    mappings.remove(mappings.indexOf(mapping));
                    return;
                }


                jAimCurPath = mapping.getAimJsonPath();
                jAimCurKey = jAimCur.getName();
                jAimCurNode = JsonMember.getJsonNodeByPath(this.jAim, mapping.getAimJsonPath());
                //获取jAimCur关联的Node的子Node
                jAimCurChild_Node = ((ArrayNode) jAimCurNode).get(0);


                jOrgCurPath = mapping.getOrgJsonPath();
                jOrgCurKey = jOrgCur.getName();
                jOrgCurNode = JsonMember.getJsonNodeByPath(this.jOrg, mapping.getOrgJsonPath());


                /****************执行转换******************* */


                if (mapping.getOptions() != null) {
                    //1:追加新元素到数组/对象 2：替换数组/对象的原有属性  默认为1
                    if (mapping.getOptions().getAddElementsOption() == 2) {

                        if (!this.replaceAimArr.stream().filter(path -> path.equals(mapping.getAimJsonPath())).findFirst().isPresent()) {
                            //清空jAimCur的子元素
                            jAimCur.getChildren().clear();
                            //清空jAimCur关联的Node的子Node
                            ((ArrayNode) jAimCurNode).removeAll();

                            this.replaceAimArr.add(mapping.getAimJsonPath());
                        }
                    }

                    //1:将源子元素复制到目标,使用源子元素的Key 2:将源子元素复制到目标,为源子元素新生成Key 3：依据源元素在目标构建同等数量的目标子元素，且如果目标的子元素存在相应的一对一映射【TranWay=2】,则创建源和目标的一对一映射,当前映射一旦设置【TranOP=3】，则TranWay属性设置无效 默认为1
                    //如果转换操作是 "TranOP":"3",
                    if (mapping.getOptions().getTranOP() == 3) {
                        for (int j = 0; j < jOrgCur.getChildren().size(); j++) {

                            JsonNode jAimCurChild_Node_Temp = null;

                            jAimCurChild_Node_Temp = mapper.readValue(mapper.writeValueAsString(jAimCurChild_Node), JsonNode.class);

                            //添加子Node到jAimCur关联的Node
                            ((ArrayNode) jAimCurNode).add(jAimCurChild_Node_Temp);

                            //创建jAimCur的子元素并添加到jAimCur
                            JsonMember jAimCurChild_Temp = this.getJsonMember(jAimCurChild_Node_Temp, "[" + (jAimCur.getChildren().size()) + "]", jAimCur);

                        }

                    } else {//1:将源子元素复制到目标,使用源子元素的Key 2:将源子元素复制到目标,为源子元素新生成Key

                        JsonNode jAimCurChild_Node_Temp = null;

                        jAimCurChild_Node_Temp = mapper.readValue(mapper.writeValueAsString(jOrgCurNode), JsonNode.class);

                        //添加子Node到jAimCur关联的Node
                        ((ArrayNode) jAimCurNode).add(jAimCurChild_Node_Temp);

                        //创建jAimCur的子元素并添加到jAimCur
                        JsonMember jAimCurChild_Temp = this.getJsonMember(jAimCurChild_Node_Temp, "[" + (jAimCur.getChildren().size()) + "]", jAimCur);

                    }
                } else {
                    JsonNode jAimCurChild_Node_Temp = null;

                    jAimCurChild_Node_Temp = mapper.readValue(mapper.writeValueAsString(jOrgCurNode), JsonNode.class);

                    //添加子Node到jAimCur关联的Node
                    ((ArrayNode) jAimCurNode).add(jAimCurChild_Node_Temp);

                    //创建jAimCur的子元素并添加到jAimCur
                    JsonMember jAimCurChild_Temp = this.getJsonMember(jAimCurChild_Node_Temp, "[" + (jAimCur.getChildren().size()) + "]", jAimCur);

                }

                //转换完成则删掉当前映射
                mappings.remove(mappings.indexOf(mapping));

                break;

            default:
                break;
        }

    }

    /**
     * 转换：对象=》对象
     */
    private void tran_Object_Object(JsonMember jOrgMember, JsonMember jAimMember, List<JsonMapping> mappings, JsonMapping mapping) throws Exception {

        JsonMember jAimCur = null;
        String jAimCurPath = null;
        String jAimCurKey = null;
        JsonNode jAimCurNode = null;

        JsonNode jAimCurChild_Node = null;
        JsonMember jAimCurChild = null;


        JsonMember jOrgCur = null;
        String jOrgCurPath = null;
        String jOrgCurKey = null;
        JsonNode jOrgCurNode = null;


        JsonNode jOrgCurChild_Node = null;
        JsonMember jOrgCurChild = null;


        String jAimChildKey = null;
        String jOrgChildKey = null;

        //设置对象新生成属性的初始索引
        int keyInitIndex = 0;

        //1：源Key->目标Key  2：源Key->目标Value  3：源Value->目标Key 4：源Value->目标Value
        switch (mapping.getTranType()) {
            case 1:
                this.tran_Key_Key(jOrgMember, jAimMember, mappings, mapping);
                break;
            case 2:

                jAimCur = this.getElementByPath(jAimMember, mapping.getAimJsonPath());
                jOrgCur = this.getElementByPath(jOrgMember, mapping.getOrgJsonPath());

                if (jOrgCur == null || jAimCur == null) {
                    if (jOrgCur == null) {

                        this.tranErrors.add(mapper.writeValueAsString(mapping) + "源路径定位不到属性！");

                    }
                    if (jAimCur == null) {
                        this.tranErrors.add(mapper.writeValueAsString(mapping) + "目标路径定位不到属性！");

                    }

                    //转换完成则删掉当前映射
                    mappings.remove(mappings.indexOf(mapping));
                    return;
                }


                jAimCurPath = mapping.getAimJsonPath();
                jAimCurKey = jAimCur.getName();
                jAimCurNode = JsonMember.getJsonNodeByPath(this.jAim, mapping.getAimJsonPath());


                jOrgCurPath = mapping.getOrgJsonPath();
                jOrgCurKey = jOrgCur.getName();
                jOrgCurNode = JsonMember.getJsonNodeByPath(this.jOrg, mapping.getOrgJsonPath());


                /*******************执行转换******************* */

                if (((ObjectNode) jAimCurNode).fields().hasNext()) {
                    jAimChildKey = ((ObjectNode) jAimCurNode).fields().next().getKey();
                }


                if (mapping.getOptions() != null) {
                    //1:追加新元素到数组/对象 2：替换数组/对象的原有属性  默认为1
                    if (mapping.getOptions().getAddElementsOption() == 2) {

                        if (!this.replaceAimArr.stream().filter(path -> path.equals(mapping.getAimJsonPath())).findFirst().isPresent()) {
                            //清空jAimCur的子元素
                            jAimCur.getChildren().clear();
                            //清空jAimCur关联的Node的子Node
                            ((ObjectNode) jAimCurNode).removeAll();

                            this.replaceAimArr.add(mapping.getAimJsonPath());
                        }
                    }

                    keyInitIndex = mapping.getOptions() != null ? (mapping.getOptions().getKeyInitIndex()) : 0;
                }


                if (jAimChildKey != null && !jAimChildKey.equals("")) {

                    String aimChildKey = jAimChildKey + "_" + String.format("%04d", jAimCur.getChildren().size() + 1) + "_" + keyInitIndex;
                    jAimCurChild_Node = JsonNodeFactory.instance.textNode(jOrgCurKey);
                    ((ObjectNode) jAimCurNode).set(aimChildKey, jAimCurChild_Node);

                    //创建jAimCur的子元素并添加到jAimCur
                    jAimCurChild = this.getJsonMember(jAimCurChild_Node, aimChildKey, jAimCur);

                }

                //转换完成则删掉当前映射
                mappings.remove(mappings.indexOf(mapping));


                break;
            case 3:
                this.tran_Value_Key(jOrgMember, jAimMember, mappings, mapping);
                break;
            case 4:

                jAimCur = this.getElementByPath(jAimMember, mapping.getAimJsonPath());
                jOrgCur = this.getElementByPath(jOrgMember, mapping.getOrgJsonPath());

                if (jOrgCur == null || jAimCur == null) {
                    if (jOrgCur == null) {

                        this.tranErrors.add(mapper.writeValueAsString(mapping) + "源路径定位不到属性！");

                    }
                    if (jAimCur == null) {
                        this.tranErrors.add(mapper.writeValueAsString(mapping) + "目标路径定位不到属性！");

                    }

                    //转换完成则删掉当前映射
                    mappings.remove(mappings.indexOf(mapping));
                    return;
                }


                jAimCurPath = mapping.getAimJsonPath();
                jAimCurKey = jAimCur.getName();
                jAimCurNode = JsonMember.getJsonNodeByPath(this.jAim, mapping.getAimJsonPath());

                if (((ObjectNode) jAimCurNode).fields().hasNext()) {
                    jAimChildKey = ((ObjectNode) jAimCurNode).fields().next().getKey();
                    jAimCurChild_Node = ((ObjectNode) jAimCurNode).fields().next().getValue();
                }


                jOrgCurPath = mapping.getOrgJsonPath();
                jOrgCurKey = jOrgCur.getName();
                jOrgCurNode = JsonMember.getJsonNodeByPath(this.jOrg, mapping.getOrgJsonPath());

                if (((ObjectNode) jOrgCurNode).fields().hasNext()) {
                    jOrgChildKey = ((ObjectNode) jOrgCurNode).fields().next().getKey();
                    jOrgCurChild_Node = ((ObjectNode) jOrgCurNode).fields().next().getValue();
                }


                /****************执行转换******************* */


                if (mapping.getOptions() != null) {
                    //1:追加新元素到数组/对象 2：替换数组/对象的原有属性  默认为1
                    if (mapping.getOptions().getAddElementsOption() == 2) {

                        if (!this.replaceAimArr.stream().filter(path -> path.equals(mapping.getAimJsonPath())).findFirst().isPresent()) {
                            //清空jAimCur的子元素
                            jAimCur.getChildren().clear();
                            //清空jAimCur关联的Node的子Node
                            ((ObjectNode) jAimCurNode).removeAll();

                            this.replaceAimArr.add(mapping.getAimJsonPath());
                        }
                    }

                    keyInitIndex = mapping.getOptions() != null ? (mapping.getOptions().getKeyInitIndex()) : 0;

                    //1:将源子元素复制到目标,使用源子元素的Key 2:将源子元素复制到目标,为源子元素新生成Key 3：依据源元素在目标构建同等数量的目标子元素，且如果目标的子元素存在相应的一对一映射【TranWay=2】,则创建源和目标的一对一映射,当前映射一旦设置【TranOP=3】，则TranWay属性设置无效 默认为1
                    //如果转换操作是 "TranOP":"3",
                    if (mapping.getOptions().getTranOP() == 3) {

                        int aimChildrenCount=   jAimCur.getChildren().size();
                        for (int j = 0; j < jOrgCur.getChildren().size(); j++) {
                            String aimChildKey = jAimChildKey + "_" + String.format("%04d",aimChildrenCount+ 1 + j) + "_" + (keyInitIndex + j);
                            JsonNode jAimCurChild_Node_Temp = null;

                            jAimCurChild_Node_Temp = mapper.readValue(mapper.writeValueAsString(jAimCurChild_Node), JsonNode.class);

                            ((ObjectNode) jAimCurNode).set(aimChildKey, jAimCurChild_Node_Temp);


                            //创建jAimCur的子元素并添加到jAimCur
                            jAimCurChild = this.getJsonMember(jAimCurChild_Node_Temp, aimChildKey, jAimCur);

                        }

                    } else if (mapping.getOptions().getTranOP() == 2) {// 2:将源子元素复制到目标,为源子元素新生成Key
                        String aimChildKey = jAimChildKey + "_" + String.format("%04d", jAimCur.getChildren().size() + 1) + "_" + (keyInitIndex);

                        JsonNode jAimCurChild_Node_Temp = null;

                        jAimCurChild_Node_Temp = mapper.readValue(mapper.writeValueAsString(jOrgCurNode), JsonNode.class);

                        ((ObjectNode) jAimCurNode).set(aimChildKey, jAimCurChild_Node_Temp);
                        //创建jAimCur的子元素并添加到jAimCur
                        jAimCurChild = this.getJsonMember(jAimCurChild_Node_Temp, aimChildKey, jAimCur);

                    } else {//1：将源子元素复制到目标,使用源子元素的Key
                        JsonNode jAimCurChild_Node_Temp = null;

                        jAimCurChild_Node_Temp = mapper.readValue(mapper.writeValueAsString(jOrgCurNode), JsonNode.class);

                        ((ObjectNode) jAimCurNode).set(jOrgCurKey, jAimCurChild_Node_Temp);
                        //创建jAimCur的子元素并添加到jAimCur
                        jAimCurChild = this.getJsonMember(jAimCurChild_Node_Temp, jOrgCurKey, jAimCur);
                    }


                } else {

                    if (jAimChildKey != null && !jAimChildKey.equals("")) {
                        JsonNode jAimCurChild_Node_Temp = null;

                        jAimCurChild_Node_Temp = mapper.readValue(mapper.writeValueAsString(jOrgCurNode), JsonNode.class);

                        ((ObjectNode) jAimCurNode).set(jOrgCurKey, jAimCurChild_Node_Temp);
                        //创建jAimCur的子元素并添加到jAimCur
                        jAimCurChild = this.getJsonMember(jAimCurChild_Node_Temp, jOrgCurKey, jAimCur);
                    }
                }

                //转换完成则删掉当前映射
                mappings.remove(mappings.indexOf(mapping));


                break;

            default:
                break;
        }
    }

    /**
     * 转换：对象=》数组
     */
    private void tran_Object_Array(JsonMember jOrgMember, JsonMember jAimMember, List<JsonMapping> mappings, JsonMapping mapping) throws Exception {

        JsonMember jAimCur = null;
        String jAimCurPath = null;
        String jAimCurKey = null;
        JsonNode jAimCurNode = null;

        JsonNode jAimCurChild_Node = null;
        JsonMember jAimCurChild = null;


        JsonMember jOrgCur = null;
        String jOrgCurPath = null;
        String jOrgCurKey = null;
        JsonNode jOrgCurNode = null;


//        String jAimChildKey = null;
        String jOrgChildKey = null;
        //设置对象新生成属性的初始索引
        int keyInitIndex = 0;

        //1：源Key->目标Key  2：源Key->目标Value  3：源Value->目标Key 4：源Value->目标Value
        switch (mapping.getTranType()) {
            case 1:
                this.tran_Key_Key(jOrgMember, jAimMember, mappings, mapping);
                break;
            case 2:

                jAimCur = this.getElementByPath(jAimMember, mapping.getAimJsonPath());
                jOrgCur = this.getElementByPath(jOrgMember, mapping.getOrgJsonPath());

                if (jOrgCur == null || jAimCur == null) {
                    if (jOrgCur == null) {

                        this.tranErrors.add(mapper.writeValueAsString(mapping) + "源路径定位不到属性！");

                    }
                    if (jAimCur == null) {
                        this.tranErrors.add(mapper.writeValueAsString(mapping) + "目标路径定位不到属性！");

                    }

                    //转换完成则删掉当前映射
                    mappings.remove(mappings.indexOf(mapping));
                    return;
                }


                jAimCurPath = mapping.getAimJsonPath();
                jAimCurKey = jAimCur.getName();
                jAimCurNode = JsonMember.getJsonNodeByPath(this.jAim, mapping.getAimJsonPath());


                jOrgCurPath = mapping.getOrgJsonPath();
                jOrgCurKey = jOrgCur.getName();
                jOrgCurNode = JsonMember.getJsonNodeByPath(this.jOrg, mapping.getOrgJsonPath());


                /*******************执行转换******************* */

//                if (((ObjectNode) jAimCurNode).fields().hasNext()) {
//                    jAimChildKey = ((ObjectNode) jAimCurNode).fields().next().getKey();
//                }


                if (mapping.getOptions() != null) {
                    //1:追加新元素到数组/对象 2：替换数组/对象的原有属性  默认为1
                    if (mapping.getOptions().getAddElementsOption() == 2) {

                        if (!this.replaceAimArr.stream().filter(path -> path.equals(mapping.getAimJsonPath())).findFirst().isPresent()) {
                            //清空jAimCur的子元素
                            jAimCur.getChildren().clear();
                            //清空jAimCur关联的Node的子Node
                            ((ArrayNode) jAimCurNode).removeAll();

                            this.replaceAimArr.add(mapping.getAimJsonPath());
                        }
                    }

                    keyInitIndex = mapping.getOptions() != null ? (mapping.getOptions().getKeyInitIndex()) : 0;
                }


                //创建jAimCur关联的Node的子Node
                jAimCurChild_Node = JsonNodeFactory.instance.textNode(jOrgCurKey);
                //添加子Node到jAimCur关联的Node
                ((ArrayNode) jAimCurNode).add(jAimCurChild_Node);

                //创建jAimCur的子元素并添加到jAimCur
                jAimCurChild = this.getJsonMember(jAimCurChild_Node, "[" + (jAimCur.getChildren().size()) + "]", jAimCur);


                //转换完成则删掉当前映射
                mappings.remove(mappings.indexOf(mapping));


                break;
            case 3:
                this.tran_Value_Key(jOrgMember, jAimMember, mappings, mapping);
                break;
            case 4:

                jAimCur = this.getElementByPath(jAimMember, mapping.getAimJsonPath());
                jOrgCur = this.getElementByPath(jOrgMember, mapping.getOrgJsonPath());

                if (jOrgCur == null || jAimCur == null) {
                    if (jOrgCur == null) {

                        this.tranErrors.add(mapper.writeValueAsString(mapping) + "源路径定位不到属性！");

                    }
                    if (jAimCur == null) {
                        this.tranErrors.add(mapper.writeValueAsString(mapping) + "目标路径定位不到属性！");

                    }

                    //转换完成则删掉当前映射
                    mappings.remove(mappings.indexOf(mapping));
                    return;
                }


                jAimCurPath = mapping.getAimJsonPath();
                jAimCurKey = jAimCur.getName();
                jAimCurNode = JsonMember.getJsonNodeByPath(this.jAim, mapping.getAimJsonPath());


                jOrgCurPath = mapping.getOrgJsonPath();
                jOrgCurKey = jOrgCur.getName();
                jOrgCurNode = JsonMember.getJsonNodeByPath(this.jOrg, mapping.getOrgJsonPath());


                /****************执行转换******************* */


                if (((ArrayNode) jAimCurNode).elements().hasNext()) {
//                    jAimChildKey = ((ArrayNode) jAimCurNode).fields().next().getKey();
                    jAimCurChild_Node = ((ArrayNode) jAimCurNode).elements().next();
                }


                if (mapping.getOptions() != null) {
                    //1:追加新元素到数组/对象 2：替换数组/对象的原有属性  默认为1
                    if (mapping.getOptions().getAddElementsOption() == 2) {

                        if (!this.replaceAimArr.stream().filter(path -> path.equals(mapping.getAimJsonPath())).findFirst().isPresent()) {
                            //清空jAimCur的子元素
                            jAimCur.getChildren().clear();
                            //清空jAimCur关联的Node的子Node
                            ((ArrayNode) jAimCurNode).removeAll();

                            this.replaceAimArr.add(mapping.getAimJsonPath());
                        }
                    }


                    //1:将源子元素复制到目标,使用源子元素的Key 2:将源子元素复制到目标,为源子元素新生成Key 3：依据源元素在目标构建同等数量的目标子元素，且如果目标的子元素存在相应的一对一映射【TranWay=2】,则创建源和目标的一对一映射,当前映射一旦设置【TranOP=3】，则TranWay属性设置无效 默认为1
                    //如果转换操作是 "TranOP":"3",
                    if (mapping.getOptions().getTranOP() == 3) {
                        for (int j = 0; j < jOrgCur.getChildren().size(); j++) {

                            JsonNode jAimCurChild_Node_Temp = null;

                            jAimCurChild_Node_Temp = mapper.readValue(mapper.writeValueAsString(jAimCurChild_Node), JsonNode.class);

                            //添加子Node到jAimCur关联的Node
                            ((ArrayNode) jAimCurNode).add(jAimCurChild_Node_Temp);

                            //创建jAimCur的子元素并添加到jAimCur
                            JsonMember jAimCurChild_Temp = this.getJsonMember(jAimCurChild_Node_Temp, "[" + (jAimCur.getChildren().size()) + "]", jAimCur);

                        }

                    } else {//1:将源子元素复制到目标,使用源子元素的Key 2:将源子元素复制到目标,为源子元素新生成Key

                        JsonNode jAimCurChild_Node_Temp = null;

                        jAimCurChild_Node_Temp = mapper.readValue(mapper.writeValueAsString(jOrgCurNode), JsonNode.class);

                        //添加子Node到jAimCur关联的Node
                        ((ArrayNode) jAimCurNode).add(jAimCurChild_Node_Temp);

                        //创建jAimCur的子元素并添加到jAimCur
                        JsonMember jAimCurChild_Temp = this.getJsonMember(jAimCurChild_Node_Temp, "[" + (jAimCur.getChildren().size()) + "]", jAimCur);

                    }
                } else {
                    JsonNode jAimCurChild_Node_Temp = null;

                    jAimCurChild_Node_Temp = mapper.readValue(mapper.writeValueAsString(jOrgCurNode), JsonNode.class);

                    //添加子Node到jAimCur关联的Node
                    ((ArrayNode) jAimCurNode).add(jAimCurChild_Node_Temp);

                    //创建jAimCur的子元素并添加到jAimCur
                    JsonMember jAimCurChild_Temp = this.getJsonMember(jAimCurChild_Node_Temp, "[" + (jAimCur.getChildren().size()) + "]", jAimCur);

                }

                //转换完成则删掉当前映射
                mappings.remove(mappings.indexOf(mapping));

                break;

            default:
                break;
        }
    }

    /**
     * 转换：数组=》对象
     */
    private void tran_Array_Object(JsonMember jOrgMember, JsonMember jAimMember, List<JsonMapping> mappings, JsonMapping mapping) throws Exception {
        JsonMember jAimCur = null;
        String jAimCurPath = null;
        String jAimCurKey = null;
        JsonNode jAimCurNode = null;

        JsonNode jAimCurChild_Node = null;
        JsonMember jAimCurChild = null;


        JsonMember jOrgCur = null;
        String jOrgCurPath = null;
        String jOrgCurKey = null;
        JsonNode jOrgCurNode = null;


        JsonNode jOrgCurChild_Node = null;
        JsonMember jOrgCurChild = null;


        String jAimChildKey = null;
//        String jOrgChildKey = null;

        //设置对象新生成属性的初始索引
        int keyInitIndex = 0;
        //1：源Key->目标Key  2：源Key->目标Value  3：源Value->目标Key 4：源Value->目标Value
        switch (mapping.getTranType()) {
            case 1:
                this.tran_Key_Key(jOrgMember, jAimMember, mappings, mapping);
                break;
            case 2:
                jAimCur = this.getElementByPath(jAimMember, mapping.getAimJsonPath());
                jOrgCur = this.getElementByPath(jOrgMember, mapping.getOrgJsonPath());

                if (jOrgCur == null || jAimCur == null) {
                    if (jOrgCur == null) {

                        this.tranErrors.add(mapper.writeValueAsString(mapping) + "源路径定位不到属性！");


                    }
                    if (jAimCur == null) {
                        this.tranErrors.add(mapper.writeValueAsString(mapping) + "目标路径定位不到属性！");


                    }

                    //转换完成则删掉当前映射
                    mappings.remove(mappings.indexOf(mapping));
                    return;
                }


                jAimCurPath = mapping.getAimJsonPath();
                jAimCurKey = jAimCur.getName();
                jAimCurNode = JsonMember.getJsonNodeByPath(this.jAim, mapping.getAimJsonPath());


                jOrgCurPath = mapping.getOrgJsonPath();
                jOrgCurKey = jOrgCur.getName();
                jOrgCurNode = JsonMember.getJsonNodeByPath(this.jOrg, mapping.getOrgJsonPath());


                /*******************执行转换******************* */

                if (((ObjectNode) jAimCurNode).fields().hasNext()) {
                    jAimChildKey = ((ObjectNode) jAimCurNode).fields().next().getKey();
                }


                if (mapping.getOptions() != null) {
                    //1:追加新元素到数组/对象 2：替换数组/对象的原有属性  默认为1
                    if (mapping.getOptions().getAddElementsOption() == 2) {

                        if (!this.replaceAimArr.stream().filter(path -> path.equals(mapping.getAimJsonPath())).findFirst().isPresent()) {
                            //清空jAimCur的子元素
                            jAimCur.getChildren().clear();
                            //清空jAimCur关联的Node的子Node
                            ((ObjectNode) jAimCurNode).removeAll();

                            this.replaceAimArr.add(mapping.getAimJsonPath());
                        }
                    }

                    keyInitIndex = mapping.getOptions() != null ? (mapping.getOptions().getKeyInitIndex()) : 0;
                }


                if (jAimChildKey != null && !jAimChildKey.equals("")) {

                    String aimChildKey = jAimChildKey + "_" + String.format("%04d", jAimCur.getChildren().size() + 1) + "_" + keyInitIndex;
                    jAimCurChild_Node = JsonNodeFactory.instance.textNode(jOrgCurKey);
                    ((ObjectNode) jAimCurNode).set(aimChildKey, jAimCurChild_Node);

                    //创建jAimCur的子元素并添加到jAimCur
                    jAimCurChild = this.getJsonMember(jAimCurChild_Node, aimChildKey, jAimCur);

                }

                //转换完成则删掉当前映射
                mappings.remove(mappings.indexOf(mapping));


                break;
            case 3:
                this.tran_Value_Key(jOrgMember, jAimMember, mappings, mapping);
                break;
            case 4:
                jAimCur = this.getElementByPath(jAimMember, mapping.getAimJsonPath());
                jOrgCur = this.getElementByPath(jOrgMember, mapping.getOrgJsonPath());

                if (jOrgCur == null || jAimCur == null) {
                    if (jOrgCur == null) {

                        this.tranErrors.add(mapper.writeValueAsString(mapping) + "源路径定位不到属性！");


                    }
                    if (jAimCur == null) {
                        this.tranErrors.add(mapper.writeValueAsString(mapping) + "目标路径定位不到属性！");


                    }

                    //转换完成则删掉当前映射
                    mappings.remove(mappings.indexOf(mapping));
                    return;
                }


                jAimCurPath = mapping.getAimJsonPath();
                jAimCurKey = jAimCur.getName();
                jAimCurNode = JsonMember.getJsonNodeByPath(this.jAim, mapping.getAimJsonPath());

                if (((ObjectNode) jAimCurNode).fields().hasNext()) {
                    jAimChildKey = ((ObjectNode) jAimCurNode).fields().next().getKey();
                    jAimCurChild_Node = ((ObjectNode) jAimCurNode).fields().next().getValue();
                }


                jOrgCurPath = mapping.getOrgJsonPath();
                jOrgCurKey = jOrgCur.getName();
                jOrgCurNode = JsonMember.getJsonNodeByPath(this.jOrg, mapping.getOrgJsonPath());

                if (((ArrayNode) jOrgCurNode).elements().hasNext()) {
//                    jOrgChildKey = ((ArrayNode) jOrgCurNode).elements().next().getKey();
                    jOrgCurChild_Node = ((ArrayNode) jOrgCurNode).elements().next();
                }


                /****************执行转换******************* */


                if (mapping.getOptions() != null) {
                    //1:追加新元素到数组/对象 2：替换数组/对象的原有属性  默认为1
                    if (mapping.getOptions().getAddElementsOption() == 2) {

                        if (!this.replaceAimArr.stream().filter(path -> path.equals(mapping.getAimJsonPath())).findFirst().isPresent()) {
                            //清空jAimCur的子元素
                            jAimCur.getChildren().clear();
                            //清空jAimCur关联的Node的子Node
                            ((ObjectNode) jAimCurNode).removeAll();

                            this.replaceAimArr.add(mapping.getAimJsonPath());
                        }
                    }

                    keyInitIndex = mapping.getOptions() != null ? (mapping.getOptions().getKeyInitIndex()) : 0;

                    //1:将源子元素复制到目标,使用源子元素的Key 2:将源子元素复制到目标,为源子元素新生成Key 3：依据源元素在目标构建同等数量的目标子元素，且如果目标的子元素存在相应的一对一映射【TranWay=2】,则创建源和目标的一对一映射,当前映射一旦设置【TranOP=3】，则TranWay属性设置无效 默认为1
                    //如果转换操作是 "TranOP":"3",
                    if (mapping.getOptions().getTranOP() == 3) {
                     int aimChildrenCount=   jAimCur.getChildren().size();
                        for (int j = 0; j < jOrgCur.getChildren().size(); j++) {
                            String aimChildKey = jAimChildKey + "_" + String.format("%04d", aimChildrenCount + 1 + j) + "_" + (keyInitIndex + j);
                            JsonNode jAimCurChild_Node_Temp = null;

                            jAimCurChild_Node_Temp = mapper.readValue(mapper.writeValueAsString(jAimCurChild_Node), JsonNode.class);

                            ((ObjectNode) jAimCurNode).set(aimChildKey, jAimCurChild_Node_Temp);


                            //创建jAimCur的子元素并添加到jAimCur
                            jAimCurChild = this.getJsonMember(jAimCurChild_Node_Temp, aimChildKey, jAimCur);

                        }

                    } else if (mapping.getOptions().getTranOP() == 2) {// 2:将源子元素复制到目标,为源子元素新生成Key
                        String aimChildKey = jAimChildKey + "_" + String.format("%04d", jAimCur.getChildren().size() + 1) + "_" + (keyInitIndex);

                        JsonNode jAimCurChild_Node_Temp = null;

                        jAimCurChild_Node_Temp = mapper.readValue(mapper.writeValueAsString(jOrgCurNode), JsonNode.class);

                        ((ObjectNode) jAimCurNode).set(aimChildKey, jAimCurChild_Node_Temp);
                        //创建jAimCur的子元素并添加到jAimCur
                        jAimCurChild = this.getJsonMember(jAimCurChild_Node_Temp, aimChildKey, jAimCur);

                    } else {//1：将源子元素复制到目标,使用源子元素的Key
                        JsonNode jAimCurChild_Node_Temp = null;

                        jAimCurChild_Node_Temp = mapper.readValue(mapper.writeValueAsString(jOrgCurNode), JsonNode.class);

                        ((ObjectNode) jAimCurNode).set(jOrgCurKey, jAimCurChild_Node_Temp);
                        //创建jAimCur的子元素并添加到jAimCur
                        jAimCurChild = this.getJsonMember(jAimCurChild_Node_Temp, jOrgCurKey, jAimCur);
                    }


                } else {

                    if (jAimChildKey != null && !jAimChildKey.equals("")) {
                        JsonNode jAimCurChild_Node_Temp = null;

                        jAimCurChild_Node_Temp = mapper.readValue(mapper.writeValueAsString(jOrgCurNode), JsonNode.class);

                        ((ObjectNode) jAimCurNode).set(jOrgCurKey, jAimCurChild_Node_Temp);
                        //创建jAimCur的子元素并添加到jAimCur
                        jAimCurChild = this.getJsonMember(jAimCurChild_Node_Temp, jOrgCurKey, jAimCur);
                    }
                }

                //转换完成则删掉当前映射
                mappings.remove(mappings.indexOf(mapping));

                break;

            default:
                break;
        }
    }

    /**
     * 转换：基础值=》对象
     */
    private void tran_Base_Object(JsonMember jOrgMember, JsonMember jAimMember, List<JsonMapping> mappings, JsonMapping mapping) throws Exception {
        JsonMember jAimCur = null;
        String jAimCurPath = null;
        String jAimCurKey = null;
        JsonNode jAimCurNode = null;

        JsonNode jAimCurChild_Node = null;
        JsonMember jAimCurChild = null;


        JsonMember jOrgCur = null;
        String jOrgCurPath = null;
        String jOrgCurKey = null;
        JsonNode jOrgCurNode = null;


//        JsonNode jOrgCurChild_Node = null;
//        JsonMember jOrgCurChild = null;


        String jAimChildKey = null;
//        String jOrgChildKey = null;

        //设置对象新生成属性的初始索引
        int keyInitIndex = 0;
        //1：源Key->目标Key  2：源Key->目标Value  3：源Value->目标Key 4：源Value->目标Value
        switch (mapping.getTranType()) {
            case 1:
                this.tran_Key_Key(jOrgMember, jAimMember, mappings, mapping);
                break;
            case 2:

                jAimCur = this.getElementByPath(jAimMember, mapping.getAimJsonPath());
                jOrgCur = this.getElementByPath(jOrgMember, mapping.getOrgJsonPath());

                if (jOrgCur == null || jAimCur == null) {
                    if (jOrgCur == null) {

                        this.tranErrors.add(mapper.writeValueAsString(mapping) + "源路径定位不到属性！");


                    }
                    if (jAimCur == null) {
                        this.tranErrors.add(mapper.writeValueAsString(mapping) + "目标路径定位不到属性！");


                    }

                    //转换完成则删掉当前映射
                    mappings.remove(mappings.indexOf(mapping));
                    return;
                }


                jAimCurPath = mapping.getAimJsonPath();
                jAimCurKey = jAimCur.getName();
                jAimCurNode = JsonMember.getJsonNodeByPath(this.jAim, mapping.getAimJsonPath());


                jOrgCurPath = mapping.getOrgJsonPath();
                jOrgCurKey = jOrgCur.getName();
                jOrgCurNode = JsonMember.getJsonNodeByPath(this.jOrg, mapping.getOrgJsonPath());


                /*******************执行转换******************* */

                if (((ObjectNode) jAimCurNode).fields().hasNext()) {
                    jAimChildKey = ((ObjectNode) jAimCurNode).fields().next().getKey();
                }


                if (mapping.getOptions() != null) {
                    //1:追加新元素到数组/对象 2：替换数组/对象的原有属性  默认为1
                    if (mapping.getOptions().getAddElementsOption() == 2) {

                        if (!this.replaceAimArr.stream().filter(path -> path.equals(mapping.getAimJsonPath())).findFirst().isPresent()) {
                            //清空jAimCur的子元素
                            jAimCur.getChildren().clear();
                            //清空jAimCur关联的Node的子Node
                            ((ObjectNode) jAimCurNode).removeAll();

                            this.replaceAimArr.add(mapping.getAimJsonPath());
                        }
                    }

                    keyInitIndex = mapping.getOptions() != null ? (mapping.getOptions().getKeyInitIndex()) : 0;
                }


                if (jAimChildKey != null && !jAimChildKey.equals("")) {

                    String aimChildKey = jAimChildKey + "_" + String.format("%04d", jAimCur.getChildren().size() + 1) + "_" + keyInitIndex;
                    jAimCurChild_Node = JsonNodeFactory.instance.textNode(jOrgCurKey);
                    ((ObjectNode) jAimCurNode).set(aimChildKey, jAimCurChild_Node);

                    //创建jAimCur的子元素并添加到jAimCur
                    jAimCurChild = this.getJsonMember(jAimCurChild_Node, aimChildKey, jAimCur);

                }

                //转换完成则删掉当前映射
                mappings.remove(mappings.indexOf(mapping));

                break;
            case 3:
                this.tran_Value_Key(jOrgMember, jAimMember, mappings, mapping);
                break;
            case 4:

                jAimCur = this.getElementByPath(jAimMember, mapping.getAimJsonPath());
                jOrgCur = this.getElementByPath(jOrgMember, mapping.getOrgJsonPath());

                if (jOrgCur == null || jAimCur == null) {
                    if (jOrgCur == null) {

                        this.tranErrors.add(mapper.writeValueAsString(mapping) + "源路径定位不到属性！");


                    }
                    if (jAimCur == null) {
                        this.tranErrors.add(mapper.writeValueAsString(mapping) + "目标路径定位不到属性！");


                    }

                    //转换完成则删掉当前映射
                    mappings.remove(mappings.indexOf(mapping));
                    return;
                }


                jAimCurPath = mapping.getAimJsonPath();
                jAimCurKey = jAimCur.getName();
                jAimCurNode = JsonMember.getJsonNodeByPath(this.jAim, mapping.getAimJsonPath());

                if (((ObjectNode) jAimCurNode).fields().hasNext()) {
                    jAimChildKey = ((ObjectNode) jAimCurNode).fields().next().getKey();
                    jAimCurChild_Node = ((ObjectNode) jAimCurNode).fields().next().getValue();
                }


                jOrgCurPath = mapping.getOrgJsonPath();
                jOrgCurKey = jOrgCur.getName();
                jOrgCurNode = JsonMember.getJsonNodeByPath(this.jOrg, mapping.getOrgJsonPath());

//                if (((ObjectNode) jOrgCurNode).fields().hasNext()) {
//                    jOrgChildKey = ((ObjectNode) jOrgCurNode).fields().next().getKey();
//                    jOrgCurChild_Node = ((ObjectNode) jOrgCurNode).fields().next().getValue();
//                }


                /****************执行转换******************* */


                if (mapping.getOptions() != null) {
                    //1:追加新元素到数组/对象 2：替换数组/对象的原有属性  默认为1
                    if (mapping.getOptions().getAddElementsOption() == 2) {

                        if (!this.replaceAimArr.stream().filter(path -> path.equals(mapping.getAimJsonPath())).findFirst().isPresent()) {
                            //清空jAimCur的子元素
                            jAimCur.getChildren().clear();
                            //清空jAimCur关联的Node的子Node
                            ((ObjectNode) jAimCurNode).removeAll();

                            this.replaceAimArr.add(mapping.getAimJsonPath());
                        }
                    }

                    keyInitIndex = mapping.getOptions() != null ? (mapping.getOptions().getKeyInitIndex()) : 0;

                    //1:将源子元素复制到目标,使用源子元素的Key 2:将源子元素复制到目标,为源子元素新生成Key 3：依据源元素在目标构建同等数量的目标子元素，且如果目标的子元素存在相应的一对一映射【TranWay=2】,则创建源和目标的一对一映射,当前映射一旦设置【TranOP=3】，则TranWay属性设置无效 默认为1
                    //如果转换操作是 "TranOP":"3",
                    if (mapping.getOptions().getTranOP() == 3) {

                        String aimChildKey = jAimChildKey + "_" + String.format("%04d", jAimCur.getChildren().size() + 1) + "_" + (keyInitIndex);
                        JsonNode jAimCurChild_Node_Temp = null;

                        jAimCurChild_Node_Temp = mapper.readValue(mapper.writeValueAsString(jAimCurChild_Node), JsonNode.class);

                        ((ObjectNode) jAimCurNode).set(aimChildKey, jAimCurChild_Node_Temp);


                        //创建jAimCur的子元素并添加到jAimCur
                        jAimCurChild = this.getJsonMember(jAimCurChild_Node_Temp, aimChildKey, jAimCur);

                    } else if (mapping.getOptions().getTranOP() == 2) {// 2:将源子元素复制到目标,为源子元素新生成Key
                        String aimChildKey = jAimChildKey + "_" + String.format("%04d", jAimCur.getChildren().size() + 1) + "_" + (keyInitIndex);

                        JsonNode jAimCurChild_Node_Temp = null;

                        jAimCurChild_Node_Temp = mapper.readValue(mapper.writeValueAsString(jOrgCurNode), JsonNode.class);

                        ((ObjectNode) jAimCurNode).set(aimChildKey, jAimCurChild_Node_Temp);
                        //创建jAimCur的子元素并添加到jAimCur
                        jAimCurChild = this.getJsonMember(jAimCurChild_Node_Temp, aimChildKey, jAimCur);

                    } else {//1：将源子元素复制到目标,使用源子元素的Key
                        JsonNode jAimCurChild_Node_Temp = null;

                        jAimCurChild_Node_Temp = mapper.readValue(mapper.writeValueAsString(jOrgCurNode), JsonNode.class);

                        ((ObjectNode) jAimCurNode).set(jOrgCurKey, jAimCurChild_Node_Temp);
                        //创建jAimCur的子元素并添加到jAimCur
                        jAimCurChild = this.getJsonMember(jAimCurChild_Node_Temp, jOrgCurKey, jAimCur);
                    }


                } else {

                    if (jAimChildKey != null && !jAimChildKey.equals("")) {
                        JsonNode jAimCurChild_Node_Temp = null;

                        jAimCurChild_Node_Temp = mapper.readValue(mapper.writeValueAsString(jOrgCurNode), JsonNode.class);

                        ((ObjectNode) jAimCurNode).set(jOrgCurKey, jAimCurChild_Node_Temp);
                        //创建jAimCur的子元素并添加到jAimCur
                        jAimCurChild = this.getJsonMember(jAimCurChild_Node_Temp, jOrgCurKey, jAimCur);
                    }
                }

                //转换完成则删掉当前映射
                mappings.remove(mappings.indexOf(mapping));


                break;

            default:
                break;
        }
    }

    /**
     * 转换：基础值=》数组
     */
    private void tran_Base_Array(JsonMember jOrgMember, JsonMember jAimMember, List<JsonMapping> mappings, JsonMapping mapping) throws Exception {

        JsonMember jAimCur = null;
        String jAimCurPath = null;
        String jAimCurKey = null;
        JsonNode jAimCurNode = null;

        JsonNode jAimCurChild_Node = null;
        JsonMember jAimCurChild = null;


        JsonMember jOrgCur = null;
        String jOrgCurPath = null;
        String jOrgCurKey = null;
        JsonNode jOrgCurNode = null;


        String jAimChildKey = null;
        String jOrgChildKey = null;
        //设置对象新生成属性的初始索引
        int keyInitIndex = 0;
        //1：源Key->目标Key  2：源Key->目标Value  3：源Value->目标Key 4：源Value->目标Value
        switch (mapping.getTranType()) {
            case 1:
                this.tran_Key_Key(jOrgMember, jAimMember, mappings, mapping);
                break;
            case 2:


                jAimCur = this.getElementByPath(jAimMember, mapping.getAimJsonPath());
                jOrgCur = this.getElementByPath(jOrgMember, mapping.getOrgJsonPath());

                if (jOrgCur == null || jAimCur == null) {
                    if (jOrgCur == null) {

                        this.tranErrors.add(mapper.writeValueAsString(mapping) + "源路径定位不到属性！");


                    }
                    if (jAimCur == null) {
                        this.tranErrors.add(mapper.writeValueAsString(mapping) + "目标路径定位不到属性！");


                    }

                    //转换完成则删掉当前映射
                    mappings.remove(mappings.indexOf(mapping));
                    return;
                }


                jAimCurPath = mapping.getAimJsonPath();
                jAimCurKey = jAimCur.getName();
                jAimCurNode = JsonMember.getJsonNodeByPath(this.jAim, mapping.getAimJsonPath());


                jOrgCurPath = mapping.getOrgJsonPath();
                jOrgCurKey = jOrgCur.getName();
                jOrgCurNode = JsonMember.getJsonNodeByPath(this.jOrg, mapping.getOrgJsonPath());


                /*******************执行转换******************* */


                if (mapping.getOptions() != null) {
                    //1:追加新元素到数组/对象 2：替换数组/对象的原有属性  默认为1
                    if (mapping.getOptions().getAddElementsOption() == 2) {

                        if (!this.replaceAimArr.stream().filter(path -> path.equals(mapping.getAimJsonPath())).findFirst().isPresent()) {
                            //清空jAimCur的子元素
                            jAimCur.getChildren().clear();
                            //清空jAimCur关联的Node的子Node
                            ((ArrayNode) jAimCurNode).removeAll();

                            this.replaceAimArr.add(mapping.getAimJsonPath());
                        }
                    }

                    keyInitIndex = mapping.getOptions() != null ? (mapping.getOptions().getKeyInitIndex()) : 0;
                }


                //创建jAimCur关联的Node的子Node
                jAimCurChild_Node = JsonNodeFactory.instance.textNode(jOrgCurKey);
                //添加子Node到jAimCur关联的Node
                ((ArrayNode) jAimCurNode).add(jAimCurChild_Node);

                //创建jAimCur的子元素并添加到jAimCur
                jAimCurChild = this.getJsonMember(jAimCurChild_Node, "[" + (jAimCur.getChildren().size()) + "]", jAimCur);


                //转换完成则删掉当前映射
                mappings.remove(mappings.indexOf(mapping));


                break;
            case 3:
                this.tran_Value_Key(jOrgMember, jAimMember, mappings, mapping);
                break;
            case 4:


                jAimCur = this.getElementByPath(jAimMember, mapping.getAimJsonPath());
                jOrgCur = this.getElementByPath(jOrgMember, mapping.getOrgJsonPath());

                if (jOrgCur == null || jAimCur == null) {
                    if (jOrgCur == null) {

                        this.tranErrors.add(mapper.writeValueAsString(mapping) + "源路径定位不到属性！");


                    }
                    if (jAimCur == null) {
                        this.tranErrors.add(mapper.writeValueAsString(mapping) + "目标路径定位不到属性！");


                    }

                    //转换完成则删掉当前映射
                    mappings.remove(mappings.indexOf(mapping));
                    return;
                }


                jAimCurPath = mapping.getAimJsonPath();
                jAimCurKey = jAimCur.getName();
                jAimCurNode = JsonMember.getJsonNodeByPath(this.jAim, mapping.getAimJsonPath());


                jOrgCurPath = mapping.getOrgJsonPath();
                jOrgCurKey = jOrgCur.getName();
                jOrgCurNode = JsonMember.getJsonNodeByPath(this.jOrg, mapping.getOrgJsonPath());


                /****************执行转换******************* */

                if (((ArrayNode) jAimCurNode).elements().hasNext()) {
                    jAimCurChild_Node = ((ArrayNode) jAimCurNode).elements().next();
                }


                if (mapping.getOptions() != null) {
                    //1:追加新元素到数组/对象 2：替换数组/对象的原有属性  默认为1
                    if (mapping.getOptions().getAddElementsOption() == 2) {

                        if (!this.replaceAimArr.stream().filter(path -> path.equals(mapping.getAimJsonPath())).findFirst().isPresent()) {
                            //清空jAimCur的子元素
                            jAimCur.getChildren().clear();
                            //清空jAimCur关联的Node的子Node
                            ((ArrayNode) jAimCurNode).removeAll();

                            this.replaceAimArr.add(mapping.getAimJsonPath());
                        }
                    }


                    //1:将源子元素复制到目标,使用源子元素的Key 2:将源子元素复制到目标,为源子元素新生成Key 3：依据源元素在目标构建同等数量的目标子元素，且如果目标的子元素存在相应的一对一映射【TranWay=2】,则创建源和目标的一对一映射,当前映射一旦设置【TranOP=3】，则TranWay属性设置无效 默认为1
                    //如果转换操作是 "TranOP":"3",
                    if (mapping.getOptions().getTranOP() == 3) {
                        JsonNode jAimCurChild_Node_Temp = null;

                        jAimCurChild_Node_Temp = mapper.readValue(mapper.writeValueAsString(jAimCurChild_Node), JsonNode.class);
                        //添加子Node到jAimCur关联的Node
                        ((ArrayNode) jAimCurNode).add(jAimCurChild_Node_Temp);

                        //创建jAimCur的子元素并添加到jAimCur
                        JsonMember jAimCurChild_Temp = this.getJsonMember(jAimCurChild_Node_Temp, "[" + (jAimCur.getChildren().size()) + "]", jAimCur);

                    } else {//1:将源子元素复制到目标,使用源子元素的Key 2:将源子元素复制到目标,为源子元素新生成Key

                        JsonNode jAimCurChild_Node_Temp = null;

                        jAimCurChild_Node_Temp = mapper.readValue(mapper.writeValueAsString(jOrgCurNode), JsonNode.class);

                        //添加子Node到jAimCur关联的Node
                        ((ArrayNode) jAimCurNode).add(jAimCurChild_Node_Temp);

                        //创建jAimCur的子元素并添加到jAimCur
                        JsonMember jAimCurChild_Temp = this.getJsonMember(jAimCurChild_Node_Temp, "[" + (jAimCur.getChildren().size()) + "]", jAimCur);

                    }
                } else {
                    JsonNode jAimCurChild_Node_Temp = null;

                    jAimCurChild_Node_Temp = mapper.readValue(mapper.writeValueAsString(jOrgCurNode), JsonNode.class);

                    //添加子Node到jAimCur关联的Node
                    ((ArrayNode) jAimCurNode).add(jAimCurChild_Node_Temp);

                    //创建jAimCur的子元素并添加到jAimCur
                    JsonMember jAimCurChild_Temp = this.getJsonMember(jAimCurChild_Node_Temp, "[" + (jAimCur.getChildren().size()) + "]", jAimCur);

                }

                //转换完成则删掉当前映射
                mappings.remove(mappings.indexOf(mapping));

                break;

            default:
                break;
        }
    }

    /**
     * 转换：数组=》基础值
     */
    private void tran_Array_Base(JsonMember jOrgMember, JsonMember jAimMember, List<JsonMapping> mappings, JsonMapping mapping) throws Exception {

        JsonMember jAimCur = null;
        String jAimCurPath = null;
        String jAimCurKey = null;
        JsonNode jAimCurNode = null;
        JsonNode jAimCurParentNode = null;
        JsonMember jAimCurParent = null;

        JsonNode jAimCurChild_Node = null;
        JsonMember jAimCurChild = null;


        JsonMember jOrgCur = null;
        String jOrgCurPath = null;
        String jOrgCurKey = null;
        JsonNode jOrgCurNode = null;

        JsonNode jOrgCurChild_Node = null;
        JsonMember jOrgCurChild = null;

        int curNodeIndex = 0;
        Iterator<JsonNode> jAimCurParentChildren = null;
        JsonNode jAimCurChild_Node_Temp = null;

        //1：源Key->目标Key  2：源Key->目标Value  3：源Value->目标Key 4：源Value->目标Value
        switch (mapping.getTranType()) {
            case 1:
                this.tran_Key_Key(jOrgMember, jAimMember, mappings, mapping);
                break;
            case 2:
                jAimCur = this.getElementByPath(jAimMember, mapping.getAimJsonPath());
                jOrgCur = this.getElementByPath(jOrgMember, mapping.getOrgJsonPath());

                if (jOrgCur == null || jAimCur == null) {
                    if (jOrgCur == null) {

                        this.tranErrors.add(mapper.writeValueAsString(mapping) + "源路径定位不到属性！");


                    }
                    if (jAimCur == null) {
                        this.tranErrors.add(mapper.writeValueAsString(mapping) + "目标路径定位不到属性！");


                    }

                    //转换完成则删掉当前映射
                    mappings.remove(mappings.indexOf(mapping));
                    return;
                }


                jAimCurPath = mapping.getAimJsonPath();
                jAimCurKey = jAimCur.getName();
                jAimCurNode = JsonMember.getJsonNodeByPath(this.jAim, mapping.getAimJsonPath());
                jAimCurParent = jAimCur.getParent();
                jAimCurParentNode = JsonMember.getJsonNodeByPath(this.jAim, jAimCur.getParent().getPath());


                jOrgCurPath = mapping.getOrgJsonPath();
                jOrgCurKey = jOrgCur.getName();
                jOrgCurNode = JsonMember.getJsonNodeByPath(this.jOrg, mapping.getOrgJsonPath());


                /*******************执行转换******************* */

                switch (jAimCurParentNode.getNodeType()) {
                    case ARRAY:
                        jAimCurParentChildren = ((ArrayNode) jAimCurParentNode).elements();
                        while (jAimCurParentChildren.hasNext()) {
                            if (jAimCurParentChildren.next().equals(jAimCurNode)) {
                                break;
                            }
                            curNodeIndex++;
                        }
                        ((ArrayNode) jAimCurParentNode).remove(curNodeIndex);
                        ((ArrayNode) jAimCurParentNode).insert(curNodeIndex, TextNode.valueOf(jOrgCurKey));

                        break;
                    case OBJECT:

                        jAimCurParentChildren = ((ObjectNode) jAimCurParentNode).elements();
                        while (jAimCurParentChildren.hasNext()) {
                            if (jAimCurParentChildren.next().equals(jAimCurNode)) {
                                break;
                            }
                            curNodeIndex++;
                        }
                        ((ObjectNode) jAimCurParentNode).set(jAimCurKey, TextNode.valueOf(jOrgCurKey));
                        break;
                    case STRING:
                    case BOOLEAN:
                    case NUMBER:
                    default:
                        break;
                }


                //转换完成则删掉当前映射
                mappings.remove(mappings.indexOf(mapping));


                break;
            case 3:
                this.tran_Value_Key(jOrgMember, jAimMember, mappings, mapping);
                break;
            case 4:

                jAimCur = this.getElementByPath(jAimMember, mapping.getAimJsonPath());
                jOrgCur = this.getElementByPath(jOrgMember, mapping.getOrgJsonPath());

                if (jOrgCur == null || jAimCur == null) {
                    if (jOrgCur == null) {

                        this.tranErrors.add(mapper.writeValueAsString(mapping) + "源路径定位不到属性！");


                    }
                    if (jAimCur == null) {
                        this.tranErrors.add(mapper.writeValueAsString(mapping) + "目标路径定位不到属性！");


                    }

                    //转换完成则删掉当前映射
                    mappings.remove(mappings.indexOf(mapping));
                    return;
                }


                jAimCurPath = mapping.getAimJsonPath();
                jAimCurKey = jAimCur.getName();
                jAimCurNode = JsonMember.getJsonNodeByPath(this.jAim, mapping.getAimJsonPath());
                jAimCurParent = jAimCur.getParent();
                jAimCurParentNode = JsonMember.getJsonNodeByPath(this.jAim, jAimCur.getParent().getPath());


                jOrgCurPath = mapping.getOrgJsonPath();
                jOrgCurKey = jOrgCur.getName();
                jOrgCurNode = JsonMember.getJsonNodeByPath(this.jOrg, mapping.getOrgJsonPath());


                /*******************执行转换******************* */

                switch (jAimCurParentNode.getNodeType()) {
                    case ARRAY:
                        jAimCurParentChildren = ((ArrayNode) jAimCurParentNode).elements();
                        while (jAimCurParentChildren.hasNext()) {
                            if (jAimCurParentChildren.next().equals(jAimCurNode)) {
                                break;
                            }
                            curNodeIndex++;
                        }
                        ((ArrayNode) jAimCurParentNode).remove(curNodeIndex);
                        jAimCurParent.getChildren().remove(jAimCur);


                        jAimCurChild_Node_Temp = mapper.readValue(mapper.writeValueAsString(jOrgCurNode), JsonNode.class);

                        ((ArrayNode) jAimCurParentNode).insert(curNodeIndex, jAimCurChild_Node_Temp);


                        //创建jAimCur的子元素并添加到jAimCur
                        jAimCurChild = this.getJsonMember(jAimCurChild_Node_Temp, "[" + curNodeIndex + "]", jAimCurParent);


                        break;
                    case OBJECT:


                        jAimCurChild_Node_Temp = mapper.readValue(mapper.writeValueAsString(jOrgCurNode), JsonNode.class);


                        ((ObjectNode) jAimCurParentNode).set(jAimCurKey, jAimCurChild_Node_Temp);
                        jAimCurParent.getChildren().remove(jAimCur);

                        //创建jAimCur的子元素并添加到jAimCur
                        jAimCurChild = this.getJsonMember(jAimCurChild_Node_Temp, jAimCurKey, jAimCurParent);


                        break;
                    case STRING:
                    case BOOLEAN:
                    case NUMBER:
                    default:
                        break;
                }


                //转换完成则删掉当前映射
                mappings.remove(mappings.indexOf(mapping));

                break;

            default:
                break;
        }

    }

    /**
     * 转换：对象=》基础值
     */
    private void tran_Object_Base(JsonMember jOrgMember, JsonMember jAimMember, List<JsonMapping> mappings, JsonMapping mapping) throws Exception {


        JsonMember jAimCur = null;
        String jAimCurPath = null;
        String jAimCurKey = null;
        JsonNode jAimCurNode = null;
        JsonNode jAimCurParentNode = null;
        JsonMember jAimCurParent = null;

        JsonNode jAimCurChild_Node = null;
        JsonMember jAimCurChild = null;


        JsonMember jOrgCur = null;
        String jOrgCurPath = null;
        String jOrgCurKey = null;
        JsonNode jOrgCurNode = null;

        JsonNode jOrgCurChild_Node = null;
        JsonMember jOrgCurChild = null;

        int curNodeIndex = 0;
        Iterator<JsonNode> jAimCurParentChildren = null;
        JsonNode jAimCurChild_Node_Temp = null;

        //1：源Key->目标Key  2：源Key->目标Value  3：源Value->目标Key 4：源Value->目标Value
        switch (mapping.getTranType()) {
            case 1:
                this.tran_Key_Key(jOrgMember, jAimMember, mappings, mapping);
                break;
            case 2:
                jAimCur = this.getElementByPath(jAimMember, mapping.getAimJsonPath());
                jOrgCur = this.getElementByPath(jOrgMember, mapping.getOrgJsonPath());

                if (jOrgCur == null || jAimCur == null) {
                    if (jOrgCur == null) {

                        this.tranErrors.add(mapper.writeValueAsString(mapping) + "源路径定位不到属性！");


                    }
                    if (jAimCur == null) {
                        this.tranErrors.add(mapper.writeValueAsString(mapping) + "目标路径定位不到属性！");


                    }

                    //转换完成则删掉当前映射
                    mappings.remove(mappings.indexOf(mapping));
                    return;
                }


                jAimCurPath = mapping.getAimJsonPath();
                jAimCurKey = jAimCur.getName();
                jAimCurNode = JsonMember.getJsonNodeByPath(this.jAim, mapping.getAimJsonPath());
                jAimCurParent = jAimCur.getParent();
                jAimCurParentNode = JsonMember.getJsonNodeByPath(this.jAim, jAimCur.getParent().getPath());


                jOrgCurPath = mapping.getOrgJsonPath();
                jOrgCurKey = jOrgCur.getName();
                jOrgCurNode = JsonMember.getJsonNodeByPath(this.jOrg, mapping.getOrgJsonPath());


                /*******************执行转换******************* */

                switch (jAimCurParentNode.getNodeType()) {
                    case ARRAY:
                        jAimCurParentChildren = ((ArrayNode) jAimCurParentNode).elements();
                        while (jAimCurParentChildren.hasNext()) {
                            if (jAimCurParentChildren.next().equals(jAimCurNode)) {
                                break;
                            }
                            curNodeIndex++;
                        }
                        ((ArrayNode) jAimCurParentNode).remove(curNodeIndex);
                        ((ArrayNode) jAimCurParentNode).insert(curNodeIndex, TextNode.valueOf(jOrgCurKey));

                        break;
                    case OBJECT:

                        jAimCurParentChildren = ((ObjectNode) jAimCurParentNode).elements();
                        while (jAimCurParentChildren.hasNext()) {
                            if (jAimCurParentChildren.next().equals(jAimCurNode)) {
                                break;
                            }
                            curNodeIndex++;
                        }
                        ((ObjectNode) jAimCurParentNode).set(jAimCurKey, TextNode.valueOf(jOrgCurKey));
                        break;
                    case STRING:
                    case BOOLEAN:
                    case NUMBER:
                    default:
                        break;
                }


                //转换完成则删掉当前映射
                mappings.remove(mappings.indexOf(mapping));


                break;
            case 3:
                this.tran_Value_Key(jOrgMember, jAimMember, mappings, mapping);
                break;
            case 4:

                jAimCur = this.getElementByPath(jAimMember, mapping.getAimJsonPath());
                jOrgCur = this.getElementByPath(jOrgMember, mapping.getOrgJsonPath());

                if (jOrgCur == null || jAimCur == null) {
                    if (jOrgCur == null) {

                        this.tranErrors.add(mapper.writeValueAsString(mapping) + "源路径定位不到属性！");


                    }
                    if (jAimCur == null) {
                        this.tranErrors.add(mapper.writeValueAsString(mapping) + "目标路径定位不到属性！");


                    }

                    //转换完成则删掉当前映射
                    mappings.remove(mappings.indexOf(mapping));
                    return;
                }


                jAimCurPath = mapping.getAimJsonPath();
                jAimCurKey = jAimCur.getName();
                jAimCurNode = JsonMember.getJsonNodeByPath(this.jAim, mapping.getAimJsonPath());
                jAimCurParent = jAimCur.getParent();
                jAimCurParentNode = JsonMember.getJsonNodeByPath(this.jAim, jAimCur.getParent().getPath());


                jOrgCurPath = mapping.getOrgJsonPath();
                jOrgCurKey = jOrgCur.getName();
                jOrgCurNode = JsonMember.getJsonNodeByPath(this.jOrg, mapping.getOrgJsonPath());


                /*******************执行转换******************* */

                switch (jAimCurParentNode.getNodeType()) {
                    case ARRAY:
                        jAimCurParentChildren = ((ArrayNode) jAimCurParentNode).elements();
                        while (jAimCurParentChildren.hasNext()) {
                            if (jAimCurParentChildren.next().equals(jAimCurNode)) {
                                break;
                            }
                            curNodeIndex++;
                        }
                        ((ArrayNode) jAimCurParentNode).remove(curNodeIndex);
                        jAimCurParent.getChildren().remove(jAimCur);


                        jAimCurChild_Node_Temp = mapper.readValue(mapper.writeValueAsString(jOrgCurNode), JsonNode.class);

                        ((ArrayNode) jAimCurParentNode).insert(curNodeIndex, jAimCurChild_Node_Temp);


                        //创建jAimCur的子元素并添加到jAimCur
                        jAimCurChild = this.getJsonMember(jAimCurChild_Node_Temp, "[" + curNodeIndex + "]", jAimCurParent);


                        break;
                    case OBJECT:


                        jAimCurChild_Node_Temp = mapper.readValue(mapper.writeValueAsString(jOrgCurNode), JsonNode.class);


                        ((ObjectNode) jAimCurParentNode).set(jAimCurKey, jAimCurChild_Node_Temp);
                        jAimCurParent.getChildren().remove(jAimCur);

                        //创建jAimCur的子元素并添加到jAimCur
                        jAimCurChild = this.getJsonMember(jAimCurChild_Node_Temp, jAimCurKey, jAimCurParent);


                        break;
                    case STRING:
                    case BOOLEAN:
                    case NUMBER:
                    default:
                        break;
                }


                //转换完成则删掉当前映射
                mappings.remove(mappings.indexOf(mapping));

                break;

            default:
                break;
        }

    }

    /**
     * 转换：基础值=》基础值
     */
    private void tran_Base_Base(JsonMember jOrgMember, JsonMember jAimMember, List<JsonMapping> mappings, JsonMapping mapping) throws Exception {


        JsonMember jAimCur = null;
        String jAimCurPath = null;
        String jAimCurKey = null;
        JsonNode jAimCurNode = null;
        JsonNode jAimCurParentNode = null;
        JsonMember jAimCurParent = null;

        JsonNode jAimCurChild_Node = null;
        JsonMember jAimCurChild = null;


        JsonMember jOrgCur = null;
        String jOrgCurPath = null;
        String jOrgCurKey = null;
        JsonNode jOrgCurNode = null;

        JsonNode jOrgCurChild_Node = null;
        JsonMember jOrgCurChild = null;

        int curNodeIndex = 0;
        Iterator<JsonNode> jAimCurParentChildren = null;
        JsonNode jAimCurChild_Node_Temp = null;

        //1：源Key->目标Key  2：源Key->目标Value  3：源Value->目标Key 4：源Value->目标Value
        switch (mapping.getTranType()) {
            case 1:
                this.tran_Key_Key(jOrgMember, jAimMember, mappings, mapping);
                break;
            case 2:
                jAimCur = this.getElementByPath(jAimMember, mapping.getAimJsonPath());
                jOrgCur = this.getElementByPath(jOrgMember, mapping.getOrgJsonPath());

                if (jOrgCur == null || jAimCur == null) {
                    if (jOrgCur == null) {

                        this.tranErrors.add(mapper.writeValueAsString(mapping) + "源路径定位不到属性！");


                    }
                    if (jAimCur == null) {
                        this.tranErrors.add(mapper.writeValueAsString(mapping) + "目标路径定位不到属性！");


                    }

                    //转换完成则删掉当前映射
                    mappings.remove(mappings.indexOf(mapping));
                    return;
                }


                jAimCurPath = mapping.getAimJsonPath();
                jAimCurKey = jAimCur.getName();
                jAimCurNode = JsonMember.getJsonNodeByPath(this.jAim, mapping.getAimJsonPath());
                jAimCurParent = jAimCur.getParent();
                jAimCurParentNode = JsonMember.getJsonNodeByPath(this.jAim, jAimCur.getParent().getPath());


                jOrgCurPath = mapping.getOrgJsonPath();
                jOrgCurKey = jOrgCur.getName();
                jOrgCurNode = JsonMember.getJsonNodeByPath(this.jOrg, mapping.getOrgJsonPath());


                /*******************执行转换******************* */

                switch (jAimCurParentNode.getNodeType()) {
                    case ARRAY:
                        jAimCurParentChildren = ((ArrayNode) jAimCurParentNode).elements();
                        while (jAimCurParentChildren.hasNext()) {
                            if (jAimCurParentChildren.next().equals(jAimCurNode)) {
                                break;
                            }
                            curNodeIndex++;
                        }
                        ((ArrayNode) jAimCurParentNode).remove(curNodeIndex);
                        ((ArrayNode) jAimCurParentNode).insert(curNodeIndex, TextNode.valueOf(jOrgCurKey));

                        break;
                    case OBJECT:

                        jAimCurParentChildren = ((ObjectNode) jAimCurParentNode).elements();
                        while (jAimCurParentChildren.hasNext()) {
                            if (jAimCurParentChildren.next().equals(jAimCurNode)) {
                                break;
                            }
                            curNodeIndex++;
                        }
                        ((ObjectNode) jAimCurParentNode).set(jAimCurKey, TextNode.valueOf(jOrgCurKey));
                        break;
                    case STRING:
                    case BOOLEAN:
                    case NUMBER:
                    default:
                        break;
                }


                //转换完成则删掉当前映射
                mappings.remove(mappings.indexOf(mapping));


                break;
            case 3:
                this.tran_Value_Key(jOrgMember, jAimMember, mappings, mapping);
                break;
            case 4:

                jAimCur = this.getElementByPath(jAimMember, mapping.getAimJsonPath());
                jOrgCur = this.getElementByPath(jOrgMember, mapping.getOrgJsonPath());
                //判断原映射是否是变量/函数
                Boolean result = Pattern.compile("^#.*#$").matcher(mapping.getOrgJsonPath()).matches();
                if (jOrgCur == null && !result || jAimCur == null) {
                    if (jOrgCur == null) {

                        this.tranErrors.add(mapper.writeValueAsString(mapping) + "源路径定位不到属性！");


                    }
                    if (jAimCur == null) {
                        this.tranErrors.add(mapper.writeValueAsString(mapping) + "目标路径定位不到属性！");


                    }

                    //转换完成则删掉当前映射
                    mappings.remove(mappings.indexOf(mapping));
                    return;
                }


                jAimCurPath = mapping.getAimJsonPath();
                jAimCurKey = jAimCur.getName();
                jAimCurNode = JsonMember.getJsonNodeByPath(this.jAim, mapping.getAimJsonPath());
                jAimCurParent = jAimCur.getParent();
                jAimCurParentNode = JsonMember.getJsonNodeByPath(this.jAim, jAimCur.getParent().getPath());


                jOrgCurPath = mapping.getOrgJsonPath();
                jOrgCurKey =jOrgCur!=null? jOrgCur.getName():"";
                jOrgCurNode = JsonMember.getJsonNodeByPath(this.jOrg, mapping.getOrgJsonPath());


                /*******************执行转换******************* */

                switch (jAimCurParentNode.getNodeType()) {
                    case ARRAY:
                        jAimCurParentChildren = ((ArrayNode) jAimCurParentNode).elements();
                        while (jAimCurParentChildren.hasNext()) {
                            if (jAimCurParentChildren.next().equals(jAimCurNode)) {
                                break;
                            }
                            curNodeIndex++;
                        }
                        ((ArrayNode) jAimCurParentNode).remove(curNodeIndex);
                        jAimCurParent.getChildren().remove(jAimCur);


                        //如果源的映射没有定位到元素
                        if (jOrgCur == null) {
                            //判断原映射是否是变量/函数
                            if (isValidMapping(jOrgMember,jAimMember,mapping,1)) {
                                jAimCurChild_Node_Temp = JsonNodeFactory.instance.textNode(getVaraibleValue(mapping.getOrgJsonPath()));
                                ((ArrayNode) jAimCurParentNode).insert(curNodeIndex, jAimCurChild_Node_Temp);
                                //创建jAimCur的子元素并添加到jAimCur
                                jAimCurChild = this.getJsonMember(jAimCurChild_Node_Temp, "[" + curNodeIndex + "]", jAimCurParent);
                            }
                        } else {
                            jAimCurChild_Node_Temp = mapper.readValue(mapper.writeValueAsString(jOrgCurNode), JsonNode.class);
                            ((ArrayNode) jAimCurParentNode).insert(curNodeIndex, jAimCurChild_Node_Temp);
                            //创建jAimCur的子元素并添加到jAimCur
                            jAimCurChild = this.getJsonMember(jAimCurChild_Node_Temp, "[" + curNodeIndex + "]", jAimCurParent);
                        }


                        break;
                    case OBJECT:


                        //如果源的映射没有定位到元素
                        if (jOrgCur == null) {
                            //判断原映射是否是变量/函数
                            if (isValidMapping(jOrgMember,jAimMember,mapping,1)) {
                                jAimCurChild_Node_Temp = JsonNodeFactory.instance.textNode(getVaraibleValue(mapping.getOrgJsonPath()));

                                ((ObjectNode) jAimCurParentNode).set(jAimCurKey, jAimCurChild_Node_Temp);
                                jAimCurParent.getChildren().remove(jAimCur);

                                //创建jAimCur的子元素并添加到jAimCur
                                jAimCurChild = this.getJsonMember(jAimCurChild_Node_Temp, jAimCurKey, jAimCurParent);
                            }
                        } else {
                            jAimCurChild_Node_Temp = mapper.readValue(mapper.writeValueAsString(jOrgCurNode), JsonNode.class);

                            ((ObjectNode) jAimCurParentNode).set(jAimCurKey, jAimCurChild_Node_Temp);
                            jAimCurParent.getChildren().remove(jAimCur);

                            //创建jAimCur的子元素并添加到jAimCur
                            jAimCurChild = this.getJsonMember(jAimCurChild_Node_Temp, jAimCurKey, jAimCurParent);
                        }


                        break;
                    case STRING:
                    case BOOLEAN:
                    case NUMBER:
                    default:
                        break;
                }


                //转换完成则删掉当前映射
                mappings.remove(mappings.indexOf(mapping));

                break;

            default:
                break;
        }
    }

    /**
     * 转换（通用）：1：Key=>Key
     *
     * @param jOrgMember
     * @param jAimMember
     * @param mappings
     * @param mapping
     */
    private void tran_Key_Key(JsonMember jOrgMember, JsonMember jAimMember, List<JsonMapping> mappings, JsonMapping mapping) throws Exception {

        JsonMember jAimCur = null;
        String jAimCurPath = null;
        String jAimCurKey = null;
        JsonNode jAimCurNode = null;
        JsonNode jAimCurParentNode = null;
        JsonMember jAimCurParent = null;

        JsonNode jAimCurChild_Node = null;
        JsonMember jAimCurChild = null;


        JsonMember jOrgCur = null;
        String jOrgCurPath = null;
        String jOrgCurKey = null;
        JsonNode jOrgCurNode = null;

        JsonNode jOrgCurChild_Node = null;
        JsonMember jOrgCurChild = null;

        int curNodeIndex = 0;
        Iterator<JsonNode> jAimCurParentChildren = null;
        JsonNode jAimCurChild_Node_Temp = null;


        jAimCur = this.getElementByPath(jAimMember, mapping.getAimJsonPath());
        jOrgCur = this.getElementByPath(jOrgMember, mapping.getOrgJsonPath());

        if (jOrgCur == null || jAimCur == null) {
            if (jOrgCur == null) {

                this.tranErrors.add(mapper.writeValueAsString(mapping) + "源路径定位不到属性！");


            }
            if (jAimCur == null) {

                this.tranErrors.add(mapper.writeValueAsString(mapping) + "目标路径定位不到属性！");


            }

            //转换完成则删掉当前映射
            mappings.remove(mappings.indexOf(mapping));
            return;
        }


        jAimCurPath = mapping.getAimJsonPath();
        jAimCurKey = jAimCur.getName();
        jAimCurNode = JsonMember.getJsonNodeByPath(this.jAim, mapping.getAimJsonPath());
        jAimCurParent = jAimCur.getParent();
        jAimCurParentNode = JsonMember.getJsonNodeByPath(this.jAim, jAimCur.getParent().getPath());


        jOrgCurPath = mapping.getOrgJsonPath();
        jOrgCurKey = jOrgCur.getName();
        jOrgCurNode = JsonMember.getJsonNodeByPath(this.jOrg, mapping.getOrgJsonPath());

        String newJAimCurPath = jAimCurPath;

        /*******************执行转换******************* */

        switch (jAimCurParentNode.getNodeType()) {
            case ARRAY:
                //*=》数组 的key-key不支持
                break;
            case OBJECT:


                jAimCurChild_Node_Temp = mapper.readValue(mapper.writeValueAsString(jAimCurNode), JsonNode.class);


                ((ObjectNode) jAimCurParentNode).remove(jAimCurKey);
                jAimCurParent.getChildren().remove(jAimCur);

                ((ObjectNode) jAimCurParentNode).set(jOrgCurKey, jAimCurChild_Node_Temp);
                //创建jAimCur的子元素并添加到jAimCur
                jAimCurChild = this.getJsonMember(jAimCurChild_Node_Temp, jOrgCurKey, jAimCurParent);

                newJAimCurPath = this.replaceLastStrs(jAimCurPath, jOrgCurKey, 2);

                break;
            case STRING:
            case BOOLEAN:
            case NUMBER:
            default:
                break;
        }

        String replaceMappting = jAimCurPath;
        Pattern regex = Pattern.compile(replaceMappting.replaceAll("\\[", "\\[").replaceAll("\\]", "\\]") + "(\\..*|\\[(\\w+|\\*).*\\]|$)");
        List<JsonMapping> relMappings1 = mappings.stream().filter(m -> {
            boolean result = regex.matcher(m.getAimJsonPath()).matches();
            return result;
        }).collect(Collectors.toList());


        String newJAimCurPathFinal = newJAimCurPath;
        relMappings1.forEach(item -> {
            item.setAimJsonPath(item.getAimJsonPath().replace(replaceMappting, newJAimCurPathFinal));
        });
        //转换完成则删掉当前映射
        mappings.remove(mappings.indexOf(mapping));

    }

    /**
     * 转换（通用）：2：Key=>Value
     *
     * @param jOrgMember
     * @param jAimMember
     * @param mappings
     * @param mapping
     */
    private void tran_Key_Value(JsonMember jOrgMember, JsonMember jAimMember, List<JsonMapping> mappings, JsonMapping mapping) throws Exception {

        //由于每种类型的Key到Value的转换都不一样，所以此方法不实现
        throw new Exception("此方法未实现！");

    }

    /**
     * 转换（通用）：3：Value=>Key
     *
     * @param jOrgMember
     * @param jAimMember
     * @param mappings
     * @param mapping
     */
    private void tran_Value_Key(JsonMember jOrgMember, JsonMember jAimMember, List<JsonMapping> mappings, JsonMapping mapping) throws Exception {


        JsonMember jAimCur = null;
        String jAimCurPath = null;
        String jAimCurKey = null;
        JsonNode jAimCurNode = null;
        JsonNode jAimCurParentNode = null;
        JsonMember jAimCurParent = null;

        JsonNode jAimCurChild_Node = null;
        JsonMember jAimCurChild = null;


        JsonMember jOrgCur = null;
        String jOrgCurPath = null;
        String jOrgCurKey = null;
        JsonNode jOrgCurNode = null;

        JsonNode jOrgCurChild_Node = null;
        JsonMember jOrgCurChild = null;

        int curNodeIndex = 0;
        Iterator<JsonNode> jAimCurParentChildren = null;
        JsonNode jAimCurChild_Node_Temp = null;


        jAimCur = this.getElementByPath(jAimMember, mapping.getAimJsonPath());
        jOrgCur = this.getElementByPath(jOrgMember, mapping.getOrgJsonPath());

        if (jOrgCur == null || jAimCur == null) {
            if (jOrgCur == null) {

                this.tranErrors.add(mapper.writeValueAsString(mapping) + "源路径定位不到属性！");


            }
            if (jAimCur == null) {

                this.tranErrors.add(mapper.writeValueAsString(mapping) + "目标路径定位不到属性！");


            }

            //转换完成则删掉当前映射
            mappings.remove(mappings.indexOf(mapping));
            return;
        }


        jAimCurPath = mapping.getAimJsonPath();
        jAimCurKey = jAimCur.getName();
        jAimCurNode = JsonMember.getJsonNodeByPath(this.jAim, mapping.getAimJsonPath());
        jAimCurParent = jAimCur.getParent();
        jAimCurParentNode = JsonMember.getJsonNodeByPath(this.jAim, jAimCur.getParent().getPath());


        jOrgCurPath = mapping.getOrgJsonPath();
        jOrgCurKey = jOrgCur.getName();
        jOrgCurNode = JsonMember.getJsonNodeByPath(this.jOrg, mapping.getOrgJsonPath());

        String newJAimCurPath = jAimCurPath;

        /*******************执行转换******************* */

        switch (jAimCurParentNode.getNodeType()) {
            case ARRAY:
                //*=》数组 的key-key不支持
                break;
            case OBJECT:


                jAimCurChild_Node_Temp = mapper.readValue(mapper.writeValueAsString(jAimCurNode), JsonNode.class);

                ((ObjectNode) jAimCurParentNode).remove(jAimCurKey);
                jAimCurParent.getChildren().remove(jAimCur);


                ((ObjectNode) jAimCurParentNode).set(jOrgCurNode.asText(), jAimCurChild_Node_Temp);
                //创建jAimCur的子元素并添加到jAimCur
                jAimCurChild = this.getJsonMember(jAimCurChild_Node_Temp, jOrgCurNode.asText(), jAimCurParent);

                newJAimCurPath = this.replaceLastStrs(jAimCurPath, jOrgCur.getType(), 2);

                break;
            case STRING:
            case BOOLEAN:
            case NUMBER:
            default:
                break;
        }

        String replaceMappting = mapping.getAimJsonPath();


        Pattern regex = Pattern.compile(replaceMappting.replaceAll("\\[", "\\[").replaceAll("\\]", "\\]") + "(\\..*|\\[(\\w+|\\*).*\\]|$)");
        List<JsonMapping> relMappings1 = mappings.stream().filter(m -> {
            boolean result = regex.matcher(m.getAimJsonPath()).matches();
            return result;
        }).collect(Collectors.toList());

        String newJAimCurPathFinal = newJAimCurPath;
        relMappings1.forEach(item -> {
            item.setAimJsonPath(item.getAimJsonPath().replace(replaceMappting, newJAimCurPathFinal));
        });

        //转换完成则删掉当前映射
        mappings.remove(mappings.indexOf(mapping));

    }

    /**
     * 转换（通用）：4：Value=>Value
     *
     * @param jOrgMember
     * @param jAimMember
     * @param mappings
     * @param mapping
     */
    private void tran_Value_Value(JsonMember jOrgMember, JsonMember jAimMember, List<JsonMapping> mappings, JsonMapping mapping) throws Exception {


        //由于每种类型的Value到Value的转换都不一样，所以此方法不实现
        throw new Exception("此方法未实现！");


    }


    /**
     * 设置子元素的Path和Parent
     *
     * @param oldPath
     * @param newPath
     */
    private void setChildrenPathAndParent(JsonMember cur, String oldPath, String newPath) {

        cur.getChildren().forEach(curTempChild -> {
            curTempChild.setPath(curTempChild.getPath().replace(oldPath, newPath));
            curTempChild.setParent(cur);
            if (curTempChild.getChildren().size() > 0) {
                setChildrenPathAndParent(curTempChild, oldPath, newPath);
            }
        });

    }


//endregion


    //region 成员公共方法


    /**
     * Json转换
     *
     * @return
     */
    public String tranJson() throws Exception {

        this.jOrg = mapper.readTree(this.orgTemplate);
        this.jAim = mapper.readTree(aimTemplate);

        //构造JsonMember(包括名称、路径等信息)
        JsonMember rootOrgJsonMember = getJsonMember(jOrg, "root", null);
        JsonMember rootAimJsonMember = getJsonMember(jAim, "root", null);


        //轮询mapping并进行处理，注意这里的mappings一定是排好序的，因为会依据排序的顺序动态改变目标元素，针对目标路径包含【*、[*]】的情况会影响接下来的mapping展开操作
        this.tranLogs.add("当前映射：" + mapper.writeValueAsString(this.jsonMappings));
        this.tranLogs.add("当前目标：" + mapper.writeValueAsString(this.jAim));
        System.out.println("当前映射：" + mapper.writeValueAsString(this.jsonMappings));
        System.out.println("当前目标：" + mapper.writeValueAsString(this.jAim));

        while (this.jsonMappings.size() > 0) {
            JsonMapping mappingItem = this.jsonMappings.get(0);
            this.tranLogs.add("*************************开始****************************");
            System.out.println("*************************开始****************************");

            //源路径包含*需要特殊处理
            if (mappingItem.getOrgJsonPath().indexOf("*") >= 0) {
                this.tranLogs.add("处理源规则映射：" + mappingItem.getOrgJsonPath());
                System.out.println("处理源规则映射：" + mappingItem.getOrgJsonPath());

                this.expandMappingForPatten(rootAimJsonMember, rootOrgJsonMember, this.jsonMappings, mappingItem, 1);

            }
            //目标路径包含*需要特殊处理
            else if (mappingItem.getAimJsonPath().indexOf("*") >= 0) {
                this.tranLogs.add("处理目标规则映射：" + mappingItem.getAimJsonPath());
                System.out.println("处理目标规则映射：" + mappingItem.getAimJsonPath());

                this.expandMappingForPatten(rootAimJsonMember, rootOrgJsonMember, this.jsonMappings, mappingItem, 2);

            }
            //如果源路径和目标路径都没有包含【*、[*]】的情况，那就针对当前mapping进行转换
            else {

                this.tranLogs.add("执行转换的映射：" + mapper.writeValueAsString(mappingItem));
                System.out.println("执行转换的映射：" + mapper.writeValueAsString(mappingItem));
                this.tranJson_Inner(rootOrgJsonMember, rootAimJsonMember, this.jsonMappings, mappingItem);
            }


            this.tranLogs.add("转换后映射：" + mapper.writeValueAsString(this.jsonMappings));
            this.tranLogs.add("转换后目标：" + mapper.writeValueAsString(this.jAim.toString()));
            this.tranLogs.add("*************************结束****************************");
            System.out.println("转换后映射：" + mapper.writeValueAsString(this.jsonMappings));
            System.out.println("转换后目标：" + mapper.writeValueAsString(this.jAim.toString()));
            System.out.println("*************************结束****************************");
        }

        return this.jAim.toString();
    }

    /**
     * 检查JsonMapping信息
     *
     * @return
     */
    public List<CheckResult> checkJsonMapping() throws Exception {

        List<CheckResult> checkResults = new ArrayList<>();


        JsonNode jOrg = null; // 将JSON字符串转换为JsonNode对象

        jOrg = mapper.readTree(this.orgTemplate);


        JsonNode jAim = null; // 将JSON字符串转换为JsonNode对象

        jAim = mapper.readTree(this.aimTemplate);


        //构造JsonMember(包括名称、路径等信息)
        JsonMember rootOrgJsonMember = getJsonMember(jOrg, "root", null);
        JsonMember rootAimJsonMember = getJsonMember(jAim, "root", null);


        this.jsonMappings.forEach(jsonMapping -> {
            StringBuilder resultAimMsg = new StringBuilder();
            StringBuilder resultOrgMsg = new StringBuilder();
            CheckResult checkResult = new CheckResult(jsonMapping);

            /********************验证路径有效性********************************/

            Boolean result = Pattern.compile(Json_Path_Regex).matcher(jsonMapping.getOrgJsonPath()).matches();
            //判断原映射是否是变量/函数
            boolean result_var = Pattern.compile("^#.*#$").matcher(jsonMapping.getOrgJsonPath()).matches();

            if (!result && !result_var) {
                resultOrgMsg.append("【" + jsonMapping.getOrgJsonPath() + "】Json路径验证失败！");
            }
            result = Pattern.compile(Json_Path_Regex).matcher(jsonMapping.getAimJsonPath()).matches();
            if (!result) {
                resultAimMsg.append("【" + jsonMapping.getAimJsonPath() + "】Json路径验证失败！");
            }

            /********************验证路径是否定位到属性********************************/
            //验证源路径
            String orgJsonPath = jsonMapping.getOrgJsonPath().replace("[*]", "[0]");
            while (orgJsonPath.contains(".*")) {
                String tempOrgJsonPath = orgJsonPath.substring(0, orgJsonPath.indexOf(".*"));
                if (tempOrgJsonPath != null && !orgJsonPath.equals("")) {
                    JsonMember jsonMember = JsonMember.getJsonMemeberByPath(rootOrgJsonMember, tempOrgJsonPath);
                    if (jsonMember == null) {
                        resultOrgMsg.append("【" + jsonMapping.getOrgJsonPath() + "】Json路径无法定位到json属性！");
                    }
                    if (jsonMember.getChildren().size() <= 0) {
                        resultOrgMsg.append("【" + jsonMapping.getOrgJsonPath() + "】Json路径没有子属性！");
                    }
                    orgJsonPath = orgJsonPath.replace(tempOrgJsonPath + ".*", tempOrgJsonPath + "." + jsonMember.getChildren().get(0).getName());
                }

            }
            JsonMember jsonMember = JsonMember.getJsonMemeberByPath(rootOrgJsonMember, orgJsonPath);
            if (jsonMember == null) {
                //判断原映射是否是变量/函数
                boolean result1 = Pattern.compile("^#.*#$").matcher(jsonMapping.getOrgJsonPath()).matches();
                if (!result1) {
                    resultOrgMsg.append("【" + jsonMapping.getOrgJsonPath() + "】Json路径无法定位到json属性！");
                }
            }

            checkResult.setOrgMsg(resultOrgMsg.toString());

            //验证目标路径
            String aimJsonPath = jsonMapping.getAimJsonPath().replace("[*]", "[0]");
            while (aimJsonPath.contains(".*")) {
                String tempAimJsonPath = aimJsonPath.substring(0, aimJsonPath.indexOf(".*"));
                if (tempAimJsonPath != null && !aimJsonPath.equals("")) {
                    JsonMember jsonMemberAim = JsonMember.getJsonMemeberByPath(rootAimJsonMember, tempAimJsonPath);
                    if (jsonMemberAim == null) {
                        resultAimMsg.append("【" + jsonMapping.getAimJsonPath() + "】Json路径无法定位到json属性！");
                    }
                    if (jsonMemberAim.getChildren().size() <= 0) {
                        resultAimMsg.append("【" + jsonMapping.getAimJsonPath() + "】Json路径没有子属性！");
                    }
                    aimJsonPath = aimJsonPath.replace(tempAimJsonPath + ".*", tempAimJsonPath + "." + jsonMemberAim.getChildren().get(0).getName());
                }

            }
            JsonMember jsonMemberAim = JsonMember.getJsonMemeberByPath(rootAimJsonMember, aimJsonPath);
            if (jsonMemberAim == null) {
                resultAimMsg.append("【" + jsonMapping.getAimJsonPath() + "】Json路径无法定位到json属性！");
            }

            checkResult.setAimMsg(resultAimMsg.toString());

            checkResults.add(checkResult);

        });


        this.tranJson();


        return checkResults;
    }


    /**
     * 获取转换日志
     *
     * @return
     */
    public List<String> getTranLogs() {
        return this.tranLogs;
    }

    /**
     * 获取转换错误
     *
     * @return
     */
    public List<String> getTranErrors() {
        return this.tranErrors;
    }


    /**
     * 获取压缩后的源Json信息
     *
     * @return
     */
    public String getSimpleOrgJson() throws JsonProcessingException {


        this.jOrg = mapper.readTree(this.orgTemplate);
        this.jAim = mapper.readTree(aimTemplate);


        return this.jOrg.toString();

    }

    //endregion

}



