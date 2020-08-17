package com.zjialin.workflow.controller;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.activiti.bpmn.converter.BpmnXMLConverter;
import org.activiti.bpmn.model.BpmnModel;
import org.activiti.editor.constants.ModelDataJsonConstants;
import org.activiti.editor.language.json.converter.BpmnJsonConverter;
import org.activiti.engine.ProcessEngine;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.repository.Deployment;
import org.activiti.engine.repository.Model;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Task;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Api(tags = "流程模型")
@Controller
@RequestMapping( "/activiti" )
public class ActivitiModelController {
    private static final Logger log = LogManager.getLogger(ActivitiModelController.class);

    @Autowired
    ProcessEngine processEngine;
    @Autowired
    ObjectMapper objectMapper;

    /**
     * 新建一个空模型
     */
    @GetMapping("/create")
    @ApiOperation(value = "新建模型", notes = "新建模型")
    public void newModel(HttpServletResponse response) throws IOException {
        RepositoryService repositoryService = processEngine.getRepositoryService();
        //初始化一个空模型
        Model model = repositoryService.newModel();

        //设置一些默认信息
        String name = "new-process";
        String description = "";
        int revision = 1;
        String key = "process";

        ObjectNode modelNode = objectMapper.createObjectNode();
        modelNode.put(ModelDataJsonConstants.MODEL_NAME, name);
        modelNode.put(ModelDataJsonConstants.MODEL_DESCRIPTION, description);
        modelNode.put(ModelDataJsonConstants.MODEL_REVISION, revision);

        model.setName(name);
        model.setKey(key);
        model.setMetaInfo(modelNode.toString());

        repositoryService.saveModel(model);
        String id = model.getId();

        //完善ModelEditorSource
        ObjectNode editorNode = objectMapper.createObjectNode();
        editorNode.put("id", "canvas");
        editorNode.put("resourceId", "canvas");
        ObjectNode stencilSetNode = objectMapper.createObjectNode();
        stencilSetNode.put("namespace",
                "http://b3mn.org/stencilset/bpmn2.0#");
        editorNode.put("stencilset", stencilSetNode);
        repositoryService.addModelEditorSource(id,editorNode.toString().getBytes("utf-8"));
        response.sendRedirect("/modeler.html?modelId="+id);
    }



    /**
     * 获取所有模型
     */
    @GetMapping("/modelList")
    @ApiOperation(value = "获取模型列表", notes = "获取模型列表")
    @ResponseBody
    public Object modelList(){
        RepositoryService repositoryService = processEngine.getRepositoryService();
        return repositoryService.createModelQuery().list();
    }

    /**
     * 发布模型为流程定义
     */
    @PostMapping("/deploy")
    @ApiOperation(value = "发布模型", notes = "发布模型")
    @ResponseBody
    @ApiImplicitParams({
            @ApiImplicitParam(name = "modelId", value = "设计的流程图模型ID", dataType = "String", paramType = "query")
    })
    public Object deploy(String modelId) throws Exception {
        //获取模型
        RepositoryService repositoryService = processEngine.getRepositoryService();
        Model modelData = repositoryService.getModel(modelId);
        byte[] bytes = repositoryService.getModelEditorSource(modelData.getId());

        if (bytes == null) {
            return "模型数据为空，请先设计流程并成功保存，再进行发布。";
        }

        JsonNode modelNode = new ObjectMapper().readTree(bytes);
        BpmnModel model = new BpmnJsonConverter().convertToBpmnModel(modelNode);
        if(model.getProcesses().size()==0){
            return "数据模型不符要求，请至少设计一条主线流程。";
        }
        byte[] bpmnBytes = new BpmnXMLConverter().convertToXML(model);

        //发布流程
        String processName = modelData.getName() + ".bpmn20.xml";
        Deployment deployment = repositoryService.createDeployment()
                .name(modelData.getName())
                .addString(processName, new String(bpmnBytes, "UTF-8"))
                .deploy();

        modelData.setDeploymentId(deployment.getId());
        repositoryService.saveModel(modelData);

        return "SUCCESS";

    }

    /**
     *  启动流程
     */
    @PostMapping("/start")
    @ApiOperation(value = "启动流程", notes = "启动流程")
    @ResponseBody
    @ApiImplicitParams({
            @ApiImplicitParam(name = "keyName", value = "流程key", dataType = "String", paramType = "query")
    })
    public Object startProcess(String keyName) {
        ProcessInstance process = processEngine.getRuntimeService().startProcessInstanceByKey(keyName);
        return process.getId() + " : " + process.getProcessDefinitionId();
    }

    /**
     *  提交任务
     */
    @PostMapping("/run")
    @ApiOperation(value = "完成任务", notes = "完成任务")
    @ResponseBody
    @ApiImplicitParams({
            @ApiImplicitParam(name = "processInstanceId", value = "流程实例id", dataType = "String", paramType = "query")
    })
    public Object run(String processInstanceId) {
        Task task = processEngine.getTaskService().createTaskQuery().processInstanceId(processInstanceId).singleResult();
        log.info("task {} find ", task.getId());
        processEngine.getTaskService().complete(task.getId());
        return "SUCCESS";
    }
}

