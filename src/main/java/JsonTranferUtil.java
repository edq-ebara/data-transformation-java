import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
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

    //endregion

    //region 成员构造函数

    public JsonTranferUtil(String orgTemplate, String aimTemplate, List<JsonMapping> jsonMappings) throws Exception {
        if (orgTemplate == "" || orgTemplate == null || aimTemplate == "" || aimTemplate == null || jsonMappings == null || jsonMappings.size() < 0) {
            throw new Exception("源模板、目标模板、映射关系不能为空！");
        }

        this.orgTemplate = orgTemplate;
        this.aimTemplate = aimTemplate;


        jsonMappings.sort((a, b)-> {
            if (a.getOrgJsonPath() != b.getOrgJsonPath())
            {
                return a.getOrgJsonPath().compareTo( b.getOrgJsonPath());
            }
            else if (a.getOrgJsonPath() == b.getOrgJsonPath())
            {
                if (a.getTranType() == 1 || a.getTranType() == 3)
                {
                    return -1;
                }
                if (b.getTranType() == 1 || b.getTranType() == 3)
                {
                    return 1;
                }
                return a.getTranType() - b.getTranType();
            }
            return 0;
        });

        this.jsonMappings = mapper.readValue(mapper.writeValueAsString(jsonMappings), mapper.getTypeFactory().constructCollectionType(List.class, JsonMapping.class));


    }


    //endregion


    //region 成员私有方法

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

    /**
     * 依据路径获取JsonMember
     *
     * @param jsonMember
     * @param path
     * @return
     */
    private JsonMember getJsonMemeberByPath(JsonMember jsonMember, String path) {
        if (jsonMember != null) {
            if (jsonMember.getPath().equals(path)) {
                return jsonMember;
            } else {
                if (jsonMember.getChildren().size() > 0) {
                    for (int i = 0; i < jsonMember.getChildren().size(); i++) {
                        JsonMember childJsonMember = getJsonMemeberByPath(jsonMember.getChildren().get(i), path);
                        if (childJsonMember != null) {
                            return childJsonMember;
                        }
                    }
                }
            }
        }
        return null;

    }

    /**
     * 获取Json成员
     *
     * @param jsonNode
     */
    private JsonMember getJsonMembers(JsonNode jsonNode, String jsonName, JsonMember parent) {
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
                    getJsonMembers(child.getValue(), child.getKey(), jsonMember);

                });
            }
        } else if (jsonNode.isArray()) {
            Iterator<JsonNode> children = jsonNode.elements();
            if (children != null && children.hasNext()) {
                Integer index = 0;
                while (children.hasNext()) {
                    getJsonMembers(children.next(), "[" + index.toString() + "]", jsonMember);
                    index++;
                }

            }

        }

        return jsonMember;


    }

    /**
     * 构建Mapping 初始化（仅仅只是简便类库使用者使用，自动为其补充映射）
     * 处理数组任意子元素转对象任意或指定子元素|数组任意或指定子元素（只设置了源任意子元素到目标任意或指定子元素的映射，没有设置父元素的映射（TranType=4））
     * 对象任意子元素转数组任意子元素（只设置了源任意子元素到目标任意子元素的映射），其它情况不考虑
     */
    private void buildJsonMapping_Init(List<JsonMapping> mappings) {

        List<JsonMapping> tempMappings = null;
        try {
            tempMappings = mapper.readValue(mapper.writeValueAsString(mappings), mapper.getTypeFactory().constructCollectionType(List.class, JsonMapping.class));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }


        for (int index = 0; index < tempMappings.size(); index++) {
            JsonMapping mappingItem = tempMappings.get(index);

            //源是数组情况：处理源是对象任意子元素，目标是对象任意或指定子元素|数组任意子元素或指定子元素
            String regexArr = "^[\\w|\\.|(\\[\\w\\])]+\\[\\*\\]\\.\\w*$";
            boolean matchsArr = Pattern.compile(regexArr).matcher(mappingItem.getOrgJsonPath()).matches();
            if (matchsArr) {


                int mappingItemPathIndex = mappingItem.getOrgJsonPath().indexOf("[*]");
                int mappingItemIndex = tempMappings.indexOf(mappingItem);
                String orgPath = mappings.get(mappingItemIndex).getOrgJsonPath().substring(0, mappingItemPathIndex);
                String orgPathTemp = mappingItem.getOrgJsonPath().substring(0, mappingItemPathIndex);
//                String orgPath = mappingItem.getOrgJsonPath().substring(0, mappingItem.getOrgJsonPath().indexOf("[*]"));
                String aimPath = mappingItem.getAimJsonPath().substring(0, Math.max(mappingItem.getAimJsonPath().lastIndexOf("[*]"), mappingItem.getAimJsonPath().lastIndexOf(".")));

                //生成mappings的新元素
                JsonMapping mapping = new JsonMapping(
                        aimPath,
                        orgPath,
                        4
                );
                //生成tempMappings的新元素
                JsonMapping mapping_Temp = new JsonMapping(
                        aimPath,
                        orgPathTemp,
                        4
                );
                boolean hasMapping = mappings.stream().filter(m -> m.getAimJsonPath().equals(mapping.getAimJsonPath()) && m.getTranType() == 4).findFirst().isPresent();
                if (!hasMapping) {
                    mappings.add(index, mapping);
                    tempMappings.add(index, mapping_Temp);
                }

                String orgPathFinnal = orgPathTemp;
                tempMappings.forEach(mappingItemCur -> {
                    mappingItemCur.setOrgJsonPath(mappingItemCur.getOrgJsonPath().replace(orgPathFinnal + "[*]", orgPathFinnal + "[0]"));
                });
            }

            //源是对象情况：只处理源是对象任意子元素且目标是数组任意子元素
            String regexObj = "^[\\w|\\.|(\\[\\w\\])]+\\.\\*$";
            boolean matchsObj = Pattern.compile(regexObj).matcher(mappingItem.getOrgJsonPath()).matches();
            matchsArr = Pattern.compile(regexArr).matcher(mappingItem.getAimJsonPath()).matches();
            if (matchsObj && matchsArr) {

                int mappingItemPathIndex = mappingItem.getOrgJsonPath().indexOf(".*");
                int mappingItemIndex = tempMappings.indexOf(mappingItem);
                String orgPath = mappings.get(mappingItemIndex).getOrgJsonPath().substring(0, mappingItemPathIndex);
                String orgPathTemp = mappingItem.getOrgJsonPath().substring(0, mappingItemPathIndex);
                String aimPath = mappings.get(mappingItemIndex).getAimJsonPath().substring(0, mappingItem.getAimJsonPath().lastIndexOf("[*]"));

                //生成mappings的新元素
                JsonMapping mapping = new JsonMapping(
                        aimPath,
                        orgPath,
                        4
                );
                //生成tempMappings的新元素
                JsonMapping mapping_Temp = new JsonMapping(
                        aimPath,
                        orgPathTemp,
                        4
                );

                boolean hasMapping = mappings.stream().filter(m -> m.getAimJsonPath().equals(mapping.getAimJsonPath()) && m.getTranType() == 4).findFirst().isPresent();
                if (!hasMapping) {
                    mappings.add(index, mapping);
                    tempMappings.add(index, mapping_Temp);
                }

                String orgPathFinnal = orgPathTemp;
                String aimPathFinnal = aimPath;
                tempMappings.forEach(mappingItemCur -> {
                    mappingItemCur.setOrgJsonPath(mappingItemCur.getOrgJsonPath().replace(orgPathFinnal + ".*", orgPathFinnal + ".a"));
                    mappingItemCur.setAimJsonPath(mappingItemCur.getAimJsonPath().replace(aimPathFinnal + "[*]", aimPathFinnal + "[0]"));
                });
            }
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
            temJsonNodeList.forEach(item->{
                ((ObjectNode) obj).set(item.getKey(),item.getValue());
            });
        }
        return obj;
    }


    /**
     * 构建Mapping
     *
     * @param jOrgMembers
     * @param jAimMembers
     * @param jAim_Cur
     * @param mappings
     */
    private void buildJsonMapping(JsonMember jOrgMembers, JsonMember jAimMembers, JsonMember jAim_Cur, List<JsonMapping> mappings) {

        Optional<JsonMapping> mappingOptional = null;
        JsonMapping mappingItem = null;
        List<JsonMapping> childMappings = new ArrayList<JsonMapping>();
        //当前目标属性的JValue
//        JsonNode jAim_CurValue = jAim_Cur.getValue();
        switch (jAim_Cur.getType().toUpperCase()) {
            //目标属性是个对象
            case "OBJECT":

                //【* =》对象】的情况下，映射类型只能是值到值【 TranType=4】
                mappingOptional = mappings.stream().filter(d -> d.getAimJsonPath().equals(jAim_Cur.getPath()) && d.getTranType() == 4).findFirst();
                mappingItem = mappingOptional.isPresent() ? mappingOptional.get() : null;

                if (mappingItem != null) {
                    String mappingItem_orgJsonPath = mappingItem.getOrgJsonPath();
                    String mappingItem_aimJsonPath = mappingItem.getAimJsonPath();
                    int mappingItem_tranType = mappingItem.getTranType();
                    //依据当前映射获取源的JProperty
                    //Optional<JsonMember> jOrg_Cur_Optional = orgMemberContainer.stream().filter(o -> o.getPath().equals(mappingItem_orgJsonPath)).findFirst();
                    //JsonMember jOrg_Cur = jOrg_Cur_Optional.isPresent() ? jOrg_Cur_Optional.get() : null;
                    JsonMember jOrg_Cur = this.getJsonMemeberByPath(jOrgMembers, mappingItem_orgJsonPath);
                    if (jOrg_Cur != null) {
                        //当前源属性的JValue
//                        JsonMember jOrg_CurValue = jOrg_CurProp.getValue();

                        switch (jOrg_Cur.getType()) {
                            //源属性是个对象
                            case "OBJECT":

                                childMappings = mappings.stream().filter(m -> {
                                    Boolean result = Pattern.compile("^" + jAim_Cur.getPath() + "\\.\\w*$").matcher(m.getAimJsonPath()).matches();
                                    return result;
                                }).collect(Collectors.toList());


                                //判断【对象 =》对象】的情况下，映射关系里是否包含目标对象属性的映射，如果包含说明客户端已经进行了指定在此不做默认处理，如果不存在则按默认操作将目标对象和源对象按照属性名和TransType 进行映射
                                if (childMappings.size() <= 0) {
                                    //轮询所有目标对象的所有属性构建与源对象同属性名的映射
                                    jAim_Cur.getChildren().forEach(jAim_Cur_Child -> {

                                        List<JsonMember> jOrg_Cur_ChildList = new ArrayList<JsonMember>();
                                        jOrg_Cur.getChildren().forEach(jOrg_Cur_Child -> {
                                            jOrg_Cur_ChildList.add(jOrg_Cur_Child);

                                        });


                                        Optional<JsonMember> jOrg_Cur_Child_Optional = jOrg_Cur_ChildList.stream().filter(c -> c.getName().toLowerCase().equals(jAim_Cur_Child.getName().toLowerCase())).findFirst();
                                        JsonMember jOrg_Cur_Child = jOrg_Cur_Child_Optional.isPresent() ? jOrg_Cur_Child_Optional.get() : null;
                                        if (jOrg_Cur_Child != null) {
                                            mappings.add(new JsonMapping(jAim_Cur_Child.getPath(), jOrg_Cur_Child.getPath(), mappingItem_tranType));
                                        }


                                    });

                                }

                                break;
                            case "ARRAY":

                                childMappings = mappings.stream().filter(m -> {
                                    Boolean result = Pattern.compile("^" + jAim_Cur.getPath() + "\\.\\w*$").matcher(m.getAimJsonPath()).matches() && (m.getTranType() == 3 || m.getTranType() == 4);
                                    return result;
                                }).collect(Collectors.toList());


                                //判断【数组 =》对象】的情况下，映射关系里是否包含目标对象属性的映射且转换类型是【3：源Value->目标Key或4：源Value->目标Value】，如果包含说明客户端已经进行了指定在此需进一步处理，如果不存在则将此【数组=》对象】的映射关系删掉，因为系统不知如何将其转换为对象
                                if (childMappings.size() <= 0) {
                                    mappings.remove(mappingItem);
                                } else {

                                    childMappings.forEach(item -> {
                                        mappings.remove(item);

                                    });
                                    for (int i = 0; i < childMappings.size(); i++) {
                                        JsonMapping childMapping = childMappings.get(i);

                                        List<JsonMapping> siblingMappings1 = mappings.stream().filter(m -> m.getAimJsonPath().endsWith(childMapping.getAimJsonPath())).collect(Collectors.toList());
                                        siblingMappings1.forEach(item ->
                                        {
                                            mappings.remove(item);
                                        });

                                        List<JsonMapping> childMappings1 = mappings.stream().filter(m -> m.getAimJsonPath().contains(childMapping.getAimJsonPath() + ".")).collect(Collectors.toList());

                                        childMappings1.forEach(item ->
                                        {
                                            mappings.remove(item);
                                        });

                                        for (int j = 0; j < jOrg_Cur.getChildren().size(); j++) {
                                            int pathIndex = j;
                                            JsonMapping newMapping = new JsonMapping(childMapping.getAimJsonPath() + j, childMapping.getOrgJsonPath().replace("[*]", "[" + j + "]"), childMapping.tranType);

                                            siblingMappings1.forEach(item ->
                                            {
                                                JsonMapping newMappingChild = new JsonMapping(
                                                        item.getAimJsonPath().replace(childMapping.getAimJsonPath(), newMapping.getAimJsonPath()),
                                                        Pattern.compile("^" + jOrg_Cur.getPath() + "\\[\\*\\]\\..*$").matcher(item.getOrgJsonPath()).matches() ? item.getOrgJsonPath().replace(jOrg_Cur.getPath() + "[*].", jOrg_Cur.getPath() + "[" + pathIndex + "].") : item.getOrgJsonPath(),
                                                        item.getTranType()
                                                );
                                                mappings.add(newMappingChild);
                                            });

                                            childMappings1.forEach(item ->
                                            {
                                                JsonMapping newMappingChild = new JsonMapping(
                                                        item.getAimJsonPath().replace(childMapping.getAimJsonPath() + ".", newMapping.getAimJsonPath() + "."),
                                                        Pattern.compile("^" + jOrg_Cur.getPath() + "\\[\\*\\]\\..*$").matcher(item.getOrgJsonPath()).matches() ? item.getOrgJsonPath().replace(jOrg_Cur.getPath() + "[*].", jOrg_Cur.getPath() + "[" + pathIndex + "].") : item.getOrgJsonPath(),
                                                        item.getTranType()
                                                );
                                                mappings.add(newMappingChild);
                                            });

                                            mappings.add(newMapping);
                                        }
                                    }

                                    Optional<JsonMember> jAim_CurChild_Optional = jAim_Cur.getChildren().stream().findFirst();
                                    JsonMember jAim_CurChild = jAim_CurChild_Optional.isPresent() ? jAim_CurChild_Optional.get() : null;
                                    JsonNode jAim_CurChildNode = JsonMember.getJsonNode(this.jAim, jAim_CurChild.getPath());
                                    jAim_Cur.getChildren().clear();
                                    ((ObjectNode) JsonMember.getJsonNode(jAim, jAim_Cur.getPath())).removeAll();
                                    for (int j = 0; j < jOrg_Cur.getChildren().size(); j++) {


                                        JsonNode jAimChildNode = null;
                                        try {
                                            jAimChildNode = mapper.readValue(mapper.writeValueAsString(jAim_CurChildNode), JsonNode.class);
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }

                                        JsonMember jAim_CurChild_Temp = null;
                                        try {
                                            jAim_CurChild_Temp = mapper.readValue(mapper.writeValueAsString(jAim_CurChild), JsonMember.class);
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }

                                        if (jAim_CurChild_Temp != null && jAimChildNode != null) {
                                            ((ObjectNode) JsonMember.getJsonNode(jAim, jAim_Cur.getPath())).set(jAim_CurChild_Temp.getName() + j, jAimChildNode);

                                            jAim_CurChild_Temp.setName(jAim_CurChild_Temp.getName() + j);
                                            jAim_CurChild_Temp.setParent(jAim_Cur);
                                            jAim_CurChild_Temp.setType(String.valueOf(jAim_CurChild_Temp.getType()));
//                                            jAim_CurChild_Temp.setJsonNode(jAimChildNode);
//                                            jAim_CurChild_Temp.setValue(jAimChildNode);
                                            String path = jAim_Cur.getPath() + "." + jAim_CurChild_Temp.getName();
                                            jAim_CurChild_Temp.setPath(path);


                                            //修改新生成元素的子元素的Path和Parent
                                            String jAim_CurChild_Path = jAim_CurChild.getPath();
                                            this.setChildrenPathAndParent(jAim_CurChild_Temp, jAim_CurChild_Path, path);


                                            jAim_Cur.getChildren().add(jAim_CurChild_Temp);
                                        }

                                    }


                                }
                                break;

                            default:
                                //判断【非对象和数组 =》对象】的情况下，映射关系里是否包含目标对象属性的映射，如果包含说明客户端已经进行了指定在此不做默认处理，如果不存在则将此【非对象=》对象】的映射关系删掉，因为系统不知如何将其转换为对象
                                if (mappings.stream().filter(m -> m.getAimJsonPath().contains(jAim_Cur.getPath() + ".")).collect(Collectors.toList()).size() <= 0) {
                                    mappings.remove(mappingItem);
                                }
                                break;
                        }
                    }

                    //删除当前的映射
                    mappings.remove(mappingItem);
                }
                break;
            //目标属性是个数组
            case "ARRAY":
                //【* =》数组】的情况下，映射类型只能是值到值【 TranType=4】
                mappingOptional = mappings.stream().filter(d -> d.getAimJsonPath().equals(jAim_Cur.getPath()) && d.getTranType() == 4).findFirst();
                mappingItem = mappingOptional.isPresent() ? mappingOptional.get() : null;

                String mappingItem_orgJsonPath = mappingItem.getOrgJsonPath();
                String mappingItem_aimJsonPath = mappingItem.getAimJsonPath();
                int mappingItem_tranType = mappingItem.getTranType();
                if (mappingItem != null) {
                    //依据当前映射获取源的JProperty
                    //Optional<JsonMember>  jOrg_Cur_Optional = orgMemberContainer.stream().filter(o -> o.getPath().equals(mappingItem_orgJsonPath)).findFirst();
                    //JsonMember jOrg_Cur = jOrg_Cur_Optional.isPresent() ? jOrg_Cur_Optional.get() : null;
                    JsonMember jOrg_Cur = this.getJsonMemeberByPath(jOrgMembers, mappingItem_orgJsonPath);

                    switch (jOrg_Cur.getType()) {
                        //源属性是个对象
                        case "OBJECT":

                            childMappings = mappings.stream().filter(m -> Pattern.compile("^" + jAim_Cur.getPath().replace("[", "\\[").replace("]", "\\]") + "\\[.*?\\]\\..*").matcher(m.getAimJsonPath()).matches()).collect(Collectors.toList());

                            //判断【对象 =》数组】的情况下，映射关系里是否包含目标数组属性的映射，如果包含说明客户端已经进行了指定,如果在指定的映射中源路径包含类似【a.*】则需要进一步处理，否则不做默认处理，如果不存在则将此【对象=》数组】的映射关系删掉，因为系统不知如何将其转换为数组
                            if (childMappings.size() <= 0) {
                                mappings.remove(mappingItem);
                            } else {
                                List<JsonMapping> childMappingList = childMappings.stream().filter(m -> Pattern.compile("^" + jOrg_Cur.getPath() + "\\.\\*.*").matcher(m.getOrgJsonPath()).matches()).collect(Collectors.toList());
                                childMappingList.forEach(item -> {
                                    mappings.remove(item);
                                });


                                Optional<JsonMember> jAim_CurChild_Optional = jAim_Cur.getChildren().stream().findFirst();
                                JsonMember jAim_CurChild = jAim_CurChild_Optional.isPresent() ? jAim_CurChild_Optional.get() : null;
                                JsonNode jAim_CurChildNode = JsonMember.getJsonNode(this.jAim, jAim_CurChild.getPath());
                                jAim_Cur.getChildren().clear();
                                ((ArrayNode) jAim_Cur.getJsonNode(jAim, jAim_Cur.getPath())).removeAll();
                                List<JsonMember> childOrgList = jOrg_Cur.getChildren();
                                for (int i = 0; i < childOrgList.size(); i++) {
                                    int index = i;
                                    childMappings.forEach(childMapping -> {
                                        JsonMember curChildOrg = childOrgList.get(index);
                                        mappings.add(new JsonMapping(
                                                childMapping.getAimJsonPath().replace(jAim_Cur.getPath() + "[*]", jAim_Cur.getPath() + "[" + index + "]"),
                                                childMapping.getOrgJsonPath().replace(jOrg_Cur.getPath() + ".*", curChildOrg.getPath()),
                                                childMapping.getTranType()
                                        ));

                                    });


                                    JsonNode jAimChildNode = null;
                                    try {
                                        jAimChildNode = mapper.readValue(mapper.writeValueAsString(jAim_CurChildNode), JsonNode.class);
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }

                                    JsonMember jAim_CurChild_Temp = null;
                                    try {
                                        jAim_CurChild_Temp = mapper.readValue(mapper.writeValueAsString(jAim_CurChild), JsonMember.class);
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }


                                    if (jAim_CurChild_Temp != null && jAimChildNode != null) {
                                        ((ArrayNode) JsonMember.getJsonNode(this.jAim, jAim_Cur.getPath())).add(jAimChildNode);

                                        jAim_CurChild_Temp.setName("[" + i + "]");
                                        jAim_CurChild_Temp.setParent(jAim_Cur);
                                        jAim_CurChild_Temp.setType(String.valueOf(jAim_CurChild_Temp.getType()));
//                                        jAim_CurChild_Temp.setJsonNode(jAimChildNode);
//                                        jAim_CurChild_Temp.setValue(jAimChildNode);
                                        String path = jAim_Cur.getPath() + "[" + i + "]";
                                        jAim_CurChild_Temp.setPath(path);


                                        //修改新生成元素的子元素的Path和Parent
                                        String jAim_CurChild_Path = jAim_CurChild.getPath();
                                        this.setChildrenPathAndParent(jAim_CurChild_Temp, jAim_CurChild_Path, path);


                                        jAim_Cur.getChildren().add(jAim_CurChild_Temp);
                                    }

                                }
                            }

                            break;
                        case "ARRAY":


                            childMappings = mappings.stream().filter(m -> Pattern.compile("^" + jAim_Cur.getPath().replace("[", "\\[").replace("]", "\\]") + "\\[.*?\\]\\.\\w*").matcher(m.getAimJsonPath()).matches()).collect(Collectors.toList());


                            //判断【数组 =》数组】的情况下，映射关系里是否包含目标数组属性的映射，如果包含说明客户端已经进行了指定,如果进行了指定则需要进一步处理，如果不存在则将此【对象=》数组】的映射关系删掉并依据原数组元素个数将目标数组元素添加为相等数量
                            if (childMappings.size() <= 0) {
                                Optional<JsonMember> jAim_CurChild_Optional = jAim_Cur.getChildren().stream().findFirst();
                                JsonMember jAim_CurChild = jAim_CurChild_Optional.isPresent() ? jAim_CurChild_Optional.get() : null;
                                JsonNode jAim_CurChildNode = JsonMember.getJsonNode(this.jAim, jAim_CurChild.getPath());
                                jAim_Cur.getChildren().clear();
                                ((ArrayNode) JsonMember.getJsonNode(jAim, jAim_Cur.getPath())).removeAll();
                                for (int j = 0; j < jOrg_Cur.getChildren().size(); j++) {


                                    JsonNode jAimChildNode = null;
                                    try {
                                        jAimChildNode = mapper.readValue(mapper.writeValueAsString(jAim_CurChildNode), JsonNode.class);
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }

                                    JsonMember jAim_CurChild_Temp = null;
                                    try {
                                        jAim_CurChild_Temp = mapper.readValue(mapper.writeValueAsString(jAim_CurChild), JsonMember.class);
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }


                                    if (jAim_CurChild_Temp != null && jAimChildNode != null) {
                                        ((ArrayNode) JsonMember.getJsonNode(jAim, jAim_Cur.getPath())).add(jAimChildNode);

                                        jAim_CurChild_Temp.setName(jAim_CurChild_Temp.getName() + j);
                                        jAim_CurChild_Temp.setParent(jAim_Cur);
                                        jAim_CurChild_Temp.setType(String.valueOf(jAim_CurChild_Temp.getType()));
//                                        jAim_CurChild_Temp.setJsonNode(jAimChildNode);
//                                        jAim_CurChild_Temp.setValue(jAimChildNode);
                                        String path = jAim_Cur.getPath() + "[" + j + "]";
                                        jAim_CurChild_Temp.setPath(path);

                                        //修改新生成元素的子元素的Path和Parent
                                        String jAim_CurChild_Path = jAim_CurChild.getPath();
                                        this.setChildrenPathAndParent(jAim_CurChild_Temp, jAim_CurChild_Path, path);


                                        jAim_Cur.getChildren().add(jAim_CurChild_Temp);
                                    }

                                }
                                mappings.remove(mappingItem);
                            } else {

                                Optional<JsonMapping> childMappingForPatten_Optional = childMappings.stream().filter(m -> Pattern.compile("^" + jOrg_Cur.getPath().replace("[", "\\[").replace("]", "\\]") + "\\[\\*\\]\\.\\w*").matcher(m.getOrgJsonPath()).matches()).findFirst();
                                JsonMapping childMappingForPatten = childMappingForPatten_Optional.isPresent() ? childMappingForPatten_Optional.get() : null;


                                if (childMappingForPatten != null) {
                                    for (int i = 0; i < childMappings.size(); i++) {
                                        JsonMapping childMapping = childMappings.get(i);
                                        mappings.remove(childMapping);

                                        for (int j = 0; j < jOrg_Cur.getChildren().size(); j++) {
                                            mappings.add(new JsonMapping(
                                                    childMapping.getAimJsonPath().replace(jAim_Cur.getPath() + "[*]", jAim_Cur.getPath() + "[" + j + "]"),
                                                    childMapping.getOrgJsonPath().replace(jOrg_Cur.getPath() + "[*]", jOrg_Cur.getPath() + "[" + j + "]"),
                                                    childMapping.getTranType()
                                            ));
                                        }
                                    }


                                    Optional<JsonMember> jAim_CurChild_Optional = jAim_Cur.getChildren().stream().findFirst();
                                    JsonMember jAim_CurChild = jAim_CurChild_Optional.isPresent() ? jAim_CurChild_Optional.get() : null;
                                    JsonNode jAim_CurChildNode = JsonMember.getJsonNode(this.jAim, jAim_CurChild.getPath());
                                    jAim_Cur.getChildren().clear();
                                    ((ArrayNode) jAim_Cur.getJsonNode(jAim, jAim_Cur.getPath())).removeAll();

                                    for (int j = 0; j < jOrg_Cur.getChildren().size(); j++) {

                                        JsonNode jAimChildNode = null;
                                        try {
                                            jAimChildNode = mapper.readValue(mapper.writeValueAsString(jAim_CurChildNode), JsonNode.class);
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }

                                        JsonMember jAim_CurChild_Temp = null;
                                        try {
                                            jAim_CurChild_Temp = mapper.readValue(mapper.writeValueAsString(jAim_CurChild), JsonMember.class);
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }


                                        if (jAim_CurChild_Temp != null && jAimChildNode != null) {
                                            ((ArrayNode) JsonMember.getJsonNode(jAim, jAim_Cur.getPath())).add(jAimChildNode);

                                            jAim_CurChild_Temp.setName(jAim_CurChild_Temp.getName() + j);
                                            jAim_CurChild_Temp.setParent(jAim_Cur);
                                            jAim_CurChild_Temp.setType(String.valueOf(jAim_CurChild_Temp.getType()));
//                                            jAim_CurChild_Temp.setJsonNode(jAimChildNode);
//                                            jAim_CurChild_Temp.setValue(jAimChildNode);
                                            String path = jAim_Cur.getPath() + "[" + j + "]";
                                            jAim_CurChild_Temp.setPath(path);


                                            //修改新生成元素的子元素的Path和Parent
                                            String jAim_CurChild_Path = jAim_CurChild.getPath();
                                            this.setChildrenPathAndParent(jAim_CurChild_Temp, jAim_CurChild_Path, path);


                                            jAim_Cur.getChildren().add(jAim_CurChild_Temp);
                                        }
                                    }
                                }
                            }
                            break;

                        default:
                            //判断【非对象和数组 =》对象】的情况下，映射关系里是否包含目标对象属性的映射，如果包含说明客户端已经进行了指定在此不做默认处理，如果不存在则将此【非对象=》对象】的映射关系删掉，因为系统不知如何将其转换为对象
                            if (mappings.stream().filter(m -> m.getAimJsonPath().contains(jAim_Cur.getPath() + ".")).collect(Collectors.toList()).size() <= 0) {
                                mappings.remove(mappingItem);
                            }
                            break;
                    }

                    //删除当前的映射
                    mappings.remove(mappingItem);
                }

                break;
            default:
                break;
        }


        for (int jAim_CurIndex = 0; jAim_CurIndex < jAim_Cur.getChildren().size(); jAim_CurIndex++) {
            JsonMember jAim_CurChild = jAim_Cur.getChildren().get(jAim_CurIndex);
            buildJsonMapping(jOrgMembers, jAimMembers, jAim_CurChild, mappings);
        }

    }

    /**
     * Json转换
     *
     * @param jOrgMember
     * @param jAimMember
     * @param jAim_CurMember
     * @param mapping
     */
    public void tranJson_Inner(JsonMember jOrgMember, JsonMember jAimMember, JsonMember jAim_CurMember, List<JsonMapping> mapping) {

        String jAim_Cur_Org_Path = jAim_CurMember.getPath();
        List<JsonMapping> jAim_Cur_MappingList = mapping.stream().filter(d -> d.getAimJsonPath().equals(jAim_Cur_Org_Path)).collect(Collectors.toList());

        for (int i = 0; i < jAim_Cur_MappingList.size(); i++) {
            JsonMapping mappingItem = jAim_Cur_MappingList.get(i);

            //Optional<JsonMember> jOrg_Cur_Optional = orgMemberContainer.stream().filter(jsonMember -> jsonMember.getPath().equals(mappingItem.getOrgJsonPath())).findFirst();
            //JsonMember jOrg_Cur = jOrg_Cur_Optional.isPresent() ? jOrg_Cur_Optional.get() : null;
            JsonMember jOrg_Cur = this.getJsonMemeberByPath(jOrgMember, mappingItem.getOrgJsonPath());

            JsonMember jAim_Cur_Parent = jAim_CurMember.getParent();
            JsonNode jAim_Cur_Parent_Node = JsonMember.getJsonNode(this.jAim, jAim_Cur_Parent.getPath());

            JsonNode jAimCurNode_Temp = null;
            JsonNode jAimCurNode_Temp_Node = JsonMember.getJsonNode(jAim, jAim_CurMember.getPath());
//            JsonMember jAim_Cur_Temp = null;
            int indexNode = 0;

            /// 转换类型
            /// 1：源Key->目标Key
            /// 2：源Key->目标Value
            /// 3：源Value->目标Key
            /// 4：源Value->目标Value
            switch (mappingItem.getTranType()) {
                case 1:

                    //从父元素的节点中移除当前元素的当前节点
                    if (jAim_Cur_Parent_Node.isArray()) {
                        while (((ArrayNode) jAim_Cur_Parent_Node).elements().hasNext()) {
                            JsonNode nodeItem = ((ArrayNode) jAim_Cur_Parent_Node).elements().next();
                            if (nodeItem.equals(JsonMember.getJsonNode(jAim, jAim_CurMember.getPath()))) {
                                ((ArrayNode) jAim_Cur_Parent_Node).remove(indexNode);
                            }
                            indexNode++;
                        }

                    } else if (jAim_Cur_Parent_Node.isObject()) {
                        ((ObjectNode) jAim_Cur_Parent_Node).remove(jAim_CurMember.getName());
                    }


                    //构造新节点并添加父节点
                    try {
                        jAimCurNode_Temp = mapper.readValue(mapper.writeValueAsString(jAimCurNode_Temp_Node), JsonNode.class);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }


                    if (jAim_CurMember != null && jAimCurNode_Temp != null) {
                        jAim_CurMember.setName(jOrg_Cur.getName());
                        jAim_CurMember.setParent(jAim_Cur_Parent);
                        jAim_CurMember.setType(String.valueOf(jAim_CurMember.getType()));
//                        jAim_CurMember.setJsonNode(jAimCurNode_Temp);
//                        jAim_CurMember.setValue(jAimCurNode_Temp);
                        if (jAim_Cur_Parent_Node.isArray()) {

                            String path = "[" + indexNode + "]";
                            jAim_CurMember.setPath(jAim_Cur_Parent.getPath() + path);

                            ((ArrayNode) jAim_Cur_Parent_Node).add(jAimCurNode_Temp);
                        } else if (jAim_Cur_Parent_Node.isObject()) {
                            String path = jOrg_Cur.getName();
                            jAim_CurMember.setPath(jAim_Cur_Parent.getPath() + "." + path);

                            ((ObjectNode) jAim_Cur_Parent_Node).set(jAim_CurMember.getName(), jAimCurNode_Temp);
                        }


                        //更改子元素的路径
                        String jAim_Cur_New_Path = jAim_CurMember.getPath();
                        jAim_CurMember.getChildren().forEach(child -> {
                            child.setPath(child.getPath().replace(jAim_Cur_Org_Path, jAim_Cur_New_Path));
                        });

                        //更改相应的映射信息
                        List<JsonMapping> relMappings1 = mapping.stream().filter(m -> Pattern.compile("^" + jAim_Cur_Org_Path.replace("[", "\\[").replace("]", "\\]")).matcher(m.getAimJsonPath()).matches()).collect(Collectors.toList());
                        relMappings1.forEach(item ->
                        {
                            item.setAimJsonPath(item.getAimJsonPath().replace(jAim_Cur_Org_Path, jAim_Cur_New_Path));
                        });

                    }

                    break;
                case 2:

                    //从父元素的节点中移除当前元素的当前节点

                    if (jAim_Cur_Parent_Node.isArray()) {
                        while (((ArrayNode) jAim_Cur_Parent_Node).elements().hasNext()) {
                            JsonNode nodeItem = ((ArrayNode) jAim_Cur_Parent_Node).elements().next();
                            if (nodeItem.equals(JsonMember.getJsonNode(jAim, jAim_CurMember.getPath()))) {
                                ((ArrayNode) jAim_Cur_Parent_Node).remove(indexNode);
                            }
                            indexNode++;
                        }

                    } else if (jAim_Cur_Parent_Node.isObject()) {
                        ((ObjectNode) jAim_Cur_Parent_Node).remove(jAim_CurMember.getName());
                    }


                    //构造新节点添加到父节点
                    try {
                        jAimCurNode_Temp = JsonNodeFactory.instance.textNode(jOrg_Cur.getName());

                    } catch (Exception e) {
                        e.printStackTrace();
                    }


                    if (jAimCurNode_Temp != null) {
//                        jAim_CurMember.setJsonNode(jAimCurNode_Temp);
//                        jAim_CurMember.setValue(jAimCurNode_Temp);


                        if (jAim_Cur_Parent_Node.isArray()) {
                            ((ArrayNode) jAim_Cur_Parent_Node).add(jAimCurNode_Temp);
                        } else if (jAim_Cur_Parent_Node.isObject()) {
                            ((ObjectNode) jAim_Cur_Parent_Node).set(jAim_CurMember.getName(), jAimCurNode_Temp);
                        }
                    }

                    break;
                case 3:
                    //从父元素中移除当前元素和从父元素的节点中移除当前元素的当前节点
//                    jAim_Cur_Parent.getChildren().remove(jAim_Cur);

                    if (jAim_Cur_Parent_Node.isArray()) {
                        while (((ArrayNode) jAim_Cur_Parent_Node).elements().hasNext()) {
                            JsonNode nodeItem = ((ArrayNode) jAim_Cur_Parent_Node).elements().next();
                            if (nodeItem.equals(JsonMember.getJsonNode(jAim, jAim_CurMember.getPath()))) {
                                ((ArrayNode) jAim_Cur_Parent_Node).remove(indexNode);
                            }
                            indexNode++;
                        }

                    } else if (jAim_Cur_Parent_Node.isObject()) {
                        ((ObjectNode) jAim_Cur_Parent_Node).remove(jAim_CurMember.getName());
                    }


                    //构造新元素和新节点并分别添加到父元素和父节点
                    try {
                        jAimCurNode_Temp = mapper.readValue(mapper.writeValueAsString(jAimCurNode_Temp_Node), JsonNode.class);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }


                    if (jAim_CurMember != null && jAimCurNode_Temp != null) {
                        jAim_CurMember.setName(JsonMember.getJsonNode(jOrg, jOrg_Cur.getPath()).asText());
                        jAim_CurMember.setParent(jAim_Cur_Parent);
                        jAim_CurMember.setType(String.valueOf(jAim_CurMember.getType()));
//                        jAim_CurMember.setJsonNode(jAimCurNode_Temp);
//                        jAim_CurMember.setValue(jAimCurNode_Temp);
                        if (jAim_Cur_Parent_Node.isArray()) {

                            String path = "[" + indexNode + "]";
                            jAim_CurMember.setPath(jAim_Cur_Parent.getPath() + path);

                            ((ArrayNode) jAim_Cur_Parent_Node).add(jAimCurNode_Temp);
                        } else if (jAim_Cur_Parent_Node.isObject()) {
                            String path = JsonMember.getJsonNode(jOrg, jOrg_Cur.getPath()).asText();
                            jAim_CurMember.setPath(jAim_Cur_Parent.getPath() + "." + path);

                            ((ObjectNode) jAim_Cur_Parent_Node).set(jAim_CurMember.getName(), jAimCurNode_Temp);
                        }


                        //更改子元素的路径
                        String jAim_Cur_New_Path = jAim_CurMember.getPath();
                        jAim_CurMember.getChildren().forEach(child -> {
                            child.setPath(child.getPath().replace(jAim_Cur_Org_Path, jAim_Cur_New_Path));
                        });

                        //更改相应的映射信息
                        List<JsonMapping> relMappings1 = mapping.stream().filter(m -> Pattern.compile(jAim_Cur_Org_Path.replace("[", "\\[").replace("]", "\\]") + ".*").matcher(m.getAimJsonPath()).matches()).collect(Collectors.toList());
                        relMappings1.forEach(item ->
                        {
                            item.setAimJsonPath(item.getAimJsonPath().replace(jAim_Cur_Org_Path, jAim_Cur_New_Path));
                        });


                    }


                    break;
                case 4:

                    //从父元素的节点中移除当前元素的当前节点

                    if (jAim_Cur_Parent_Node.isArray()) {
                        while (((ArrayNode) jAim_Cur_Parent_Node).elements().hasNext()) {
                            JsonNode nodeItem = ((ArrayNode) jAim_Cur_Parent_Node).elements().next();
                            if (nodeItem.equals(JsonMember.getJsonNode(jAim, jAim_CurMember.getPath()))) {
                                ((ArrayNode) jAim_Cur_Parent_Node).remove(indexNode);
                            }
                            indexNode++;
                        }

                    } else if (jAim_Cur_Parent_Node.isObject()) {
                        ((ObjectNode) jAim_Cur_Parent_Node).remove(jAim_CurMember.getName());
                    }


                    //构造新节点添加到父节点
                    try {
                        jAimCurNode_Temp = mapper.readValue(mapper.writeValueAsString(JsonMember.getJsonNode(this.jOrg, jOrg_Cur.getPath())), JsonNode.class);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }


                    if (jAimCurNode_Temp != null) {
//                        jAim_Cur.setJsonNode(jAimCurNode_Temp);
//                        jAim_Cur.setValue(jAimCurNode_Temp);

                        if (jAim_Cur_Parent_Node.isArray()) {
                            ((ArrayNode) jAim_Cur_Parent_Node).add(jAimCurNode_Temp);
                        } else if (jAim_Cur_Parent_Node.isObject()) {
                            ((ObjectNode) jAim_Cur_Parent_Node).set(jAim_CurMember.getName(), jAimCurNode_Temp);
                        }
                    }

                    break;
                default:
                    break;
            }


        }


        for (int jAim_CurIndex = 0; jAim_CurIndex < jAim_CurMember.getChildren().size(); jAim_CurIndex++) {
            JsonMember jAim_CurChild = jAim_CurMember.getChildren().get(jAim_CurIndex);
            tranJson_Inner(jOrgMember, jAimMember, jAim_CurChild, mapping);

        }
    }


//endregion


    //region 成员公共方法


    /**
     * Json转换
     *
     * @return
     */
    public String tranJson() {


        try {
            this.jOrg = mapper.readTree(this.orgTemplate);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

        try {
            this.jAim = mapper.readTree(aimTemplate);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

        //构造JsonMember(包括名称、路径等信息)
        JsonMember rootOrgJsonMember = getJsonMembers(jOrg, "root", null);
        JsonMember rootAimJsonMember = getJsonMembers(jAim, "root", null);


        System.out.println("******************转换前  JAIM **********************");
//        try {
//            System.out.println(mapper.writeValueAsString(rootAimJsonMember));
//        } catch (JsonProcessingException e) {
//            e.printStackTrace();
//        }
        System.out.println(this.jAim.toString());
        System.out.println("******************转换前  Mapping **********************");
        jsonMappings.forEach(m -> {
            try {
                System.out.println(mapper.writeValueAsString(m));
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }

        });

        buildJsonMapping_Init(jsonMappings);
        System.out.println("******************初始化后的  Mapping **********************");
        jsonMappings.forEach(m -> {
            try {
                System.out.println(mapper.writeValueAsString(m));
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }

        });

        buildJsonMapping(rootOrgJsonMember, rootAimJsonMember, rootAimJsonMember, jsonMappings);

        System.out.println("******************重新构造后的  Mapping **********************");
        jsonMappings.forEach(m -> {
            try {
                System.out.println(mapper.writeValueAsString(m));
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }

        });
        System.out.println("******************重新构造后的  JAIM **********************");
//        try {
//            System.out.println(mapper.writeValueAsString(rootAimJsonMember));
//        } catch (JsonProcessingException e) {
//            e.printStackTrace();
//        }
        System.out.println(this.jAim.toString());
        tranJson_Inner(rootOrgJsonMember, rootAimJsonMember, rootAimJsonMember, jsonMappings);


        System.out.println("******************转换后  Mapping **********************");
        jsonMappings.forEach(m -> {
            try {
                System.out.println(mapper.writeValueAsString(m));
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }

        });
        System.out.println("******************转换后  JAIM **********************");
//        try {
//            System.out.println(mapper.writeValueAsString(rootAimJsonMember).toString());
//        } catch (JsonProcessingException e) {
//            e.printStackTrace();
//        }
        System.out.println(this.jAim.toString());

        return this.jAim.toString();
    }

    /**
     * 检查JsonMapping信息
     *
     * @return
     */
    public List<CheckResult> checkJsonMapping() {

        List<CheckResult> checkResults = new ArrayList<>();


        JsonNode jOrg = null; // 将JSON字符串转换为JsonNode对象
        try {
            jOrg = mapper.readTree(this.orgTemplate);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

        JsonNode jAim = null; // 将JSON字符串转换为JsonNode对象
        try {
            jAim = mapper.readTree(this.aimTemplate);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

        //构造JsonMember(包括名称、路径等信息)
        JsonMember rootOrgJsonMember = getJsonMembers(jOrg, "root", null);
        JsonMember rootAimJsonMember = getJsonMembers(jAim, "root", null);


        this.jsonMappings.forEach(jsonMapping -> {
            StringBuilder resultAimMsg = new StringBuilder();
            StringBuilder resultOrgMsg = new StringBuilder();
            CheckResult checkResult = new CheckResult(jsonMapping);

            /********************验证路径有效性********************************/

            Boolean result = Pattern.compile(Json_Path_Regex).matcher(jsonMapping.getOrgJsonPath()).matches();
            if (!result) {
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
                    JsonMember jsonMember = getJsonMemeberByPath(rootOrgJsonMember, tempOrgJsonPath);
                    if (jsonMember == null) {
                        resultOrgMsg.append("【" + jsonMapping.getOrgJsonPath() + "】Json路径无法定位到json属性！");
                    }
                    if (jsonMember.getChildren().size() <= 0) {
                        resultOrgMsg.append("【" + jsonMapping.getOrgJsonPath() + "】Json路径没有子属性！");
                    }
                    orgJsonPath = orgJsonPath.replace(tempOrgJsonPath + ".*", tempOrgJsonPath + "." + jsonMember.getChildren().get(0).getName());
                }

            }
            JsonMember jsonMember = getJsonMemeberByPath(rootOrgJsonMember, orgJsonPath);
            if (jsonMember == null) {
                resultOrgMsg.append("【" + jsonMapping.getOrgJsonPath() + "】Json路径无法定位到json属性！");
            }

            checkResult.setOrgMsg(resultOrgMsg.toString());

            //验证目标路径
            String aimJsonPath = jsonMapping.getAimJsonPath().replace("[*]", "[0]");
            while (aimJsonPath.contains(".*")) {
                String tempAimJsonPath = aimJsonPath.substring(0, aimJsonPath.indexOf(".*"));
                if (tempAimJsonPath != null && !aimJsonPath.equals("")) {
                    JsonMember jsonMemberAim = getJsonMemeberByPath(rootAimJsonMember, tempAimJsonPath);
                    if (jsonMemberAim == null) {
                        resultAimMsg.append("【" + jsonMapping.getAimJsonPath() + "】Json路径无法定位到json属性！");
                    }
                    if (jsonMemberAim.getChildren().size() <= 0) {
                        resultAimMsg.append("【" + jsonMapping.getAimJsonPath() + "】Json路径没有子属性！");
                    }
                    aimJsonPath = aimJsonPath.replace(tempAimJsonPath + ".*", tempAimJsonPath + "." + jsonMemberAim.getChildren().get(0).getName());
                }

            }
            JsonMember jsonMemberAim = getJsonMemeberByPath(rootAimJsonMember, aimJsonPath);
            if (jsonMemberAim == null) {
                resultAimMsg.append("【" + jsonMapping.getAimJsonPath() + "】Json路径无法定位到json属性！");
            }

            checkResult.setAimMsg(resultAimMsg.toString());

            checkResults.add(checkResult);

        });

        return checkResults;
    }

    /**
     * 获取压缩后的源Json信息
     *
     * @return
     */
    public String getSimpleOrgJson() {


        try {
            this.jOrg = mapper.readTree(this.orgTemplate);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

        try {
            this.jAim = mapper.readTree(aimTemplate);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }


        //构造JsonMember(包括名称、路径等信息)
        JsonMember tempOrgTemplate = getJsonMembers(this.jOrg, "root", null);
        JsonMember tempAimTemplate = getJsonMembers(this.jAim, "root", null);

        List<JsonMapping> tempMappings = null;
        try {
            tempMappings = mapper.readValue(mapper.writeValueAsString(this.jsonMappings), mapper.getTypeFactory().constructCollectionType(List.class, JsonMapping.class));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }


        //进行初步构造位数组转*的情况，添加父级节点的映射（TranType=4）
        this.buildJsonMapping_Init(tempMappings);

        this.buildJsonMapping(tempOrgTemplate, tempAimTemplate, tempAimTemplate, tempMappings);

        List<String> tempOrgMappings = tempMappings.stream().map(JsonMapping::getOrgJsonPath)
                .collect(Collectors.toList());

        JsonNode compressJson = this.compressJson(this.jOrg, "root", tempOrgMappings);
        return compressJson.toString();

    }

    //endregion

}



