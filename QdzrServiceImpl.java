package com.fenbeitong.server.plugin.customize.qingdaozhongrui.service.impl;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fenbeitong.openapi.customize.dao.OpenKingdeeUrlConfigDao;
import com.fenbeitong.openapi.customize.entity.OpenKingdeeUrlConfig;
import com.fenbeitong.openapi.customize.util.DateUtil;
import com.fenbeitong.server.plugin.customize.qingdaozhongrui.dto.*;
import com.fenbeitong.server.plugin.customize.qingdaozhongrui.service.IQdzrService;
import com.fenbeitong.server.plugin.util.common.JsonUtils;
import com.fenbeitong.server.plugin.util.common.RestHttpUtils;
import com.fenbeitong.server.plugin.util.common.ResultVo;
import com.fenbeitong.server.plugin.util.exception.OpenApiArgumentException;
import com.finhub.framework.core.Func;
import kingdee.bos.webapi.client.K3CloudApiClient;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author machao
 * @date 2022/12/5
 */
@Service
@Slf4j
public class QdzrServiceImpl implements IQdzrService {
    private static final String PAYMENT_CREATE = "/openapi/payment/custom_order/v1/create";
    private static final String PAYMENT_DETAIL = "/openapi/payment/custom_order/v1/detail";
    private static final String EMPLOYEE_LIST = "/openapi/org/employee/v1/list";
    private static final String AP_PAY_BILL = "AP_PAYBILL";
    private static final String CN_REC_PAY_PURPOSE = "CN_RECPAYPURPOSE";
    private static final String CN_BANKACNT = "CN_BANKACNT";
    private static final String SEC_USER = "SEC_User";
    private static final int PAY_SUCCESS = 80;
    private static final int PAY_FAILURE = 21;
    private static final int PAY_REFUND = 84;

    @Value("${host.tiger}")
    private String tigerHost;

    @Value("${qdzr.account_code}")
    private String qdzrAccountCode;

    @Autowired
    private OpenKingdeeUrlConfigDao kingdeeUrlConfigDao;

    @Autowired
    private RestHttpUtils restHttpUtils;

    @Override
    public void syncPayBill(String companyId, String formId, String accountId, String accountType, String paymentStatus, String createDate) {
        if (Func.equals(createDate, "1")) {
            // 格式为 2023-03-04T00:00:00
            createDate = DateUtil.getCurrentDateStr(DateUtil.FORMAT_DATE_PATTERN) + "T00:00:00";
        }
        // 创建付款单失败的数据
        Map<Object, String> result = new HashMap<>(16);
        // 登陆
        K3CloudApiClient client = validateUser(companyId);
        // 单据查询
        List<List<Object>> lists = apPayBillQuery(client, paymentStatus, createDate);
        if (Func.isNotEmpty(lists)) {
            // 循环调用创建付款单
            for (List<Object> list : lists) {
                try {
                    invokePaymentCreate(companyId, formId, accountId, accountType,
                            // 0-账单号, 1-创建人id, 2-实付金额, 3-供应商id, 4-收款账户id
                            // 从金蝶同步过来的供应商id 与 收款账户id 一样
                            list.get(0), getThirdEmployeeId(client, list.get(1), companyId), getAmount(list.get(2)), getUse(client, list.get(3)), list.get(4), list.get(4));
                } catch (Exception e) {
                    // 统计失败数据
                    result.put(list, e.getMessage());
                }
            }
        }
        if (Func.isNotEmpty(result)) {
            log.info("创建付款单失败数据 result = {}", JsonUtils.toJson(result));
        }
    }

    @Override
    public void pushPayBillPaymentStatus(String companyId, CallbackDataPayStatus callbackData) {
        String billNo = callbackData.getThirdPaymentId();
        // 登陆
        K3CloudApiClient client = validateUser(companyId);
        // 查询三方付款单信息
        List<List<Object>> lists = executeBillQuery(client, "FID", billNo);
        if (Func.isEmpty(lists) || Func.isEmpty(lists.get(0))) {
            log.info("请求金蝶云星空查询三方付款单信息为空");
            return;
        }
        // 更新支付状态
        Map<String, Object> req = new HashMap<>(2);
        Map<String, Object> model = new HashMap<>(8);
        if (Func.isNotNull(lists.get(0))) {
            model.put("FID", lists.get(0).get(0));
        }
        model.put("FBillNo", billNo);
        if (callbackData.getPaymentState() == PAY_SUCCESS) {
            model.put("F_PAEZ_PaymentStatus", "3");
        } else if (callbackData.getPaymentState() == PAY_FAILURE || callbackData.getPaymentState() == PAY_REFUND) {
            model.put("F_PAEZ_PaymentStatus", "4");
        } else {
            log.info("不需要处理的支付状态 callbackData = {}", callbackData);
            return;
        }
        req.put("Model", model);
        try {
            // 保存
            log.info("K3CloudApiClient save req = {}", JsonUtils.toJson(req));
            String save = client.save(AP_PAY_BILL, JsonUtils.toJson(req));
            log.info("K3CloudApiClient save resp = {}", save);
        } catch (Exception e) {
            log.error("请求金蝶云星空银行付款状态接口异常", e);
            throw new OpenApiArgumentException("请求金蝶云星空银行付款状态接口异常");
        }
    }

    private List<List<Object>> executeBillQuery(K3CloudApiClient client, String fieldKeys, String billNo) {
        KingdeeBillQueryDTO reqQuery = new KingdeeBillQueryDTO();
        reqQuery.setFormId(AP_PAY_BILL);
        reqQuery.setFieldKeys(fieldKeys);
        reqQuery.setFilterString("FBillNo='" + billNo + "'");
        List<List<Object>> lists;
        try {
            log.info("K3CloudApiClient executeBillQuery req = {}", JsonUtils.toJson(reqQuery));
            lists = client.executeBillQuery(JsonUtils.toJson(reqQuery));
            log.info("K3CloudApiClient executeBillQuery resp = {}", JsonUtils.toJson(lists));
        } catch (Exception e) {
            log.error("请求金蝶云星空付款单单据查询接口异常", e);
            throw new OpenApiArgumentException("请求金蝶云星空付款单单据查询接口异常");
        }
        return lists;
    }

    @Override
    public void pushPayBillBankStatement(String companyId, CallbackDataBankStatement callbackData) {
        String billNo = callbackData.getThirdOrderId();
        // 登陆
        K3CloudApiClient client = validateUser(companyId);
        // 查询三方付款单信息
        List<List<Object>> lists = executeBillQuery(client, "FSETTLETYPEID,FREALPAYAMOUNTFOR_D,FID,FPAYBILLENTRY_FEntryID,F_PAEZ_BankStatement", billNo);
        if (Func.isEmpty(lists) || Func.isEmpty(lists.get(0)) || Func.equals(lists.get(0).get(0), "7")) {
            // 结算方式是银行承兑汇票 不回写电子回单地址
            log.info("请求金蝶云星空查询三方付款单信息为空 或 结算方式是银行承兑汇票 不回写电子回单地址 {}", JsonUtils.toJson(lists));
            return;
        }
        // 查询分贝通付款单信息
        PaymentCustomDetailRespDTO detail = paymentDetailQuery(companyId, billNo);
        if (Func.isNotNull(detail)) {
            PaymentCustomDetailRespDTO.Order order = detail.getOrder();
            if (Func.isNotNull(order)) {
                PaymentCustomDetailRespDTO.PaymentAccount paymentAccount = order.getPaymentAccount();
                if (Func.isNotNull(paymentAccount)) {
                    String accountCode = paymentAccount.getAccountCode();
                    // 付款账户不是指定的账户 或者 付款金额不是实际金额 不回写电子回单地址
                    if (!Func.equals(accountCode, qdzrAccountCode) ||
                            order.getAmount().compareTo(getAmount(lists.get(0).get(1))) != 0) {
                        log.info("付款账户不是指定的账户 或者 付款金额不是实际金额 不回写电子回单地址 accountCode = {}", accountCode);
                        return;
                    }
                }
            }
        }
        // 更新电子回单地址
        Map<String, Object> req = new HashMap<>(2);
        Map<String, Object> model = new HashMap<>(8);
        if (Func.isNotNull(lists.get(0))) {
            model.put("FID", lists.get(0).get(2));
        }
        model.put("FBillNo", billNo);
        List<Map<String, Object>> payBillEntry = new ArrayList<>();
        Map<String, Object> entry = new HashMap<>(4);
        entry.put("FEntryID", lists.get(0).get(3));
        if (Func.isNotNull(lists.get(0).get(4)) && Func.isNotBlank(lists.get(0).get(4).toString())) {
            entry.put("F_PAEZ_BankStatement", lists.get(0).get(4).toString() + "," + callbackData.getUrl());
        } else {
            entry.put("F_PAEZ_BankStatement", callbackData.getUrl());
        }
        payBillEntry.add(entry);
        model.put("FPAYBILLENTRY", payBillEntry);
        req.put("Model", model);
        try {
            log.info("K3CloudApiClient save req = {}", JsonUtils.toJson(req));
            String save = client.save(AP_PAY_BILL, JsonUtils.toJson(req));
            log.info("K3CloudApiClient save resp = {}", save);
        } catch (Exception e) {
            log.error("请求金蝶云星空同步银行状态接口异常", e);
            throw new OpenApiArgumentException("请求金蝶云星空同步银行状态接口异常");
        }
    }

    private PaymentCustomDetailRespDTO paymentDetailQuery(String companyId, String billNo) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json");
        headers.add("appId", companyId);
        String reqUrl = tigerHost + PAYMENT_DETAIL;
        Map<String, Object> reqMap = new HashMap<>(8);
        reqMap.put("third_order_id", billNo);
        try {
            String resp = restHttpUtils.postJson(reqUrl, headers, JsonUtils.toJson(reqMap));
            ResultVo result = JsonUtils.toObj(resp, ResultVo.class);
            if (result.getCode() != 0) {
                log.error("查询自定义付款单详情失败 resultVo = {}", JsonUtils.toJson(result));
                throw new OpenApiArgumentException("查询自定义付款单详情失败" + JsonUtils.toJson(result));
            }
            return JsonUtils.toObj(JsonUtils.toJson(result.getData()), PaymentCustomDetailRespDTO.class);
        } catch (Exception e) {
            log.error("付款单详情查询接口异常", e);
            throw new OpenApiArgumentException("付款单详情查询接口异常");
        }
    }

    @Override
    public void pushPayBillDelete(String companyId, CallbackDataDelete callbackData) {
        // 登陆
        K3CloudApiClient client = validateUser(companyId);
        Map<String, Object> map = new HashMap<>(2);
        CallbackDataDelete.PaymentDetailDTO payment = callbackData.getPayment();
        if (Func.isNotNull(payment)) {
            map.put("Numbers", Arrays.asList(payment.getThirdPaymentId()));
            // 先反审批然后删除
            try {
                // 反审批
                log.info("K3CloudApiClient unAudit req = {}", JsonUtils.toJson(map));
                String unAudit = client.unAudit(AP_PAY_BILL, JsonUtils.toJson(map));
                log.info("K3CloudApiClient unAudit resp = {}", unAudit);
                // 删除
                log.info("K3CloudApiClient delete req = {}", JsonUtils.toJson(map));
                String delete = client.delete(AP_PAY_BILL, JsonUtils.toJson(map));
                log.info("K3CloudApiClient delete resp = {}", delete);
            } catch (Exception e) {
                log.error("请求金蝶云星空删除接口异常", e);
                throw new OpenApiArgumentException("请求金蝶云星空删除接口异常");
            }
        }
    }

    private BigDecimal getAmount(Object amount) {
        if (amount instanceof Double) {
            Double amt = (Double) amount;
            return new BigDecimal(amt).setScale(2, RoundingMode.HALF_UP);
        } else if (amount instanceof Integer) {
            Integer amt = (Integer) amount;
            return new BigDecimal(amt).setScale(2, RoundingMode.HALF_UP);
        }
        log.error("金额转换异常 amount={}", amount);
        throw new OpenApiArgumentException("金额转换异常");
    }

    private List<List<Object>> apPayBillQuery(K3CloudApiClient client, String paymentStatus, String createDate) {
        if (Func.isBlank(createDate)) {
            throw new OpenApiArgumentException("创建时间不能为空");
        }
        int startRow = 0;
        int limit = 2000;
        List<List<Object>> listAll = new ArrayList<>();

        KingdeeBillQueryDTO req = new KingdeeBillQueryDTO();
        req.setFormId(AP_PAY_BILL);
        req.setFieldKeys("FBillNo,FCreatorId,FREALPAYAMOUNTFOR_D,FPURPOSEID,FCONTACTUNIT,FACCOUNTID");
        req.setFilterString("FDOCUMENTSTATUS='C' and F_PAEZ_PaymentStatus='" + paymentStatus + "' and FDATE = '" + createDate + "' " +
                "and FCONTACTUNITTYPE = 'BD_Supplier'");
        req.setStartRow(startRow);
        req.setLimit(limit);
        try {
            log.info("K3CloudApiClient executeBillQuery req = {}", JsonUtils.toJson(req));
            List<List<Object>> lists = client.executeBillQuery(JsonUtils.toJson(req));
            log.info("K3CloudApiClient executeBillQuery resp = {}", JsonUtils.toJson(lists));
            if (Func.isNotEmpty(lists)) {
                listAll.addAll(lists);
            }
            // 循环查询数据 每次2000条 下标递增0, 2000, 4000...
            while (Func.isNotEmpty(lists) && lists.size() == limit) {
                req.setStartRow(startRow += limit);
                log.info("K3CloudApiClient executeBillQuery req = {}", JsonUtils.toJson(req));
                lists = client.executeBillQuery(JsonUtils.toJson(req));
                log.info("K3CloudApiClient executeBillQuery resp = {}", JsonUtils.toJson(lists));
                if (Func.isNotEmpty(lists)) {
                    listAll.addAll(lists);
                }
            }
            if (Func.isEmpty(listAll)) {
                return listAll;
            }
            // 过滤我方账户
            return listAll.stream().filter(e -> {
                // 银行账户id
                if (Func.isNull(e.get(5))) {
                    return false;
                }
                String bankNumber = getBankNumber(client, e.get(5).toString());
                return Func.equals(bankNumber, qdzrAccountCode);
            }).collect(Collectors.toList());
        } catch (Exception e) {
            log.error("请求金蝶云星空付款单单据查询接口异常", e);
            throw new OpenApiArgumentException("请求金蝶云星空付款单单据查询接口异常");
        }
    }

    private String getBankNumber(K3CloudApiClient client, Object bankAcctId) {
        KingdeeBillQueryDTO req = new KingdeeBillQueryDTO();
        req.setFormId(CN_BANKACNT);
        req.setFieldKeys("FNumber");
        req.setFilterString("FBANKACNTID='" + bankAcctId + "'");
        try {
            log.info("K3CloudApiClient executeBillQuery req = {}", JsonUtils.toJson(req));
            List<List<Object>> lists = client.executeBillQuery(JsonUtils.toJson(req));
            log.info("K3CloudApiClient executeBillQuery resp = {}", JsonUtils.toJson(lists));
            if (Func.isNotEmpty(lists)) {
                List<Object> objects = lists.get(0);
                if (Func.isNotEmpty(objects)) {
                    return objects.get(0).toString();
                }
            }
            log.error("查询金蝶云星空银行账户不存在 bankAcctId= {}", bankAcctId);
            throw new OpenApiArgumentException("查询金蝶云星空银行账户不存在");
        } catch (Exception e) {
            log.error("请求金蝶云星空银行账户接口异常", e);
            throw new OpenApiArgumentException("请求金蝶云星空银行账户接口异常");
        }
    }

    private K3CloudApiClient validateUser(String companyId) {
        if (Func.isBlank(companyId)) {
            throw new OpenApiArgumentException("企业ID不能为空");
        }
        // 金蝶配置
        OpenKingdeeUrlConfig config = kingdeeUrlConfigDao.getByCompanyId(companyId);
        K3CloudApiClient client = new K3CloudApiClient(config.getUrl());
        boolean login;
        try {
            // 登陆客户端
            login = client.login(config.getAcctId(), config.getUserName(), config.getPassword(),
                    Func.isNotBlank(config.getLcid()) ? Integer.parseInt(config.getLcid()) : 2052);
            log.info("K3CloudApiClient login resp = {}", login);
        } catch (Exception e) {
            log.info("金蝶云星空登陆异常", e);
            throw new OpenApiArgumentException("金蝶云星空登陆异常");
        }
        if (!login) {
            log.info("金蝶云星空登陆失败:{}", JsonUtils.toJson(config));
            throw new OpenApiArgumentException("金蝶云星空登陆失败");
        }
        return client;
    }

    private Object getUse(K3CloudApiClient client, Object purposeId) {
        KingdeeBillQueryDTO req = new KingdeeBillQueryDTO();
        req.setFormId(CN_REC_PAY_PURPOSE);
        req.setFieldKeys("FName");
        req.setFilterString("FID='" + purposeId + "'");
        try {
            log.info("K3CloudApiClient executeBillQuery req = {}", JsonUtils.toJson(req));
            List<List<Object>> lists = client.executeBillQuery(JsonUtils.toJson(req));
            log.info("K3CloudApiClient executeBillQuery resp = {}", JsonUtils.toJson(lists));
            if (Func.isNotEmpty(lists)) {
                List<Object> objects = lists.get(0);
                if (Func.isNotEmpty(objects)) {
                    return objects.get(0);
                }
            }
            log.error("查询金蝶云星空收付款用途不存在 purposeId= {}", purposeId);
            throw new OpenApiArgumentException("查询金蝶云星空收付款用途不存在");
        } catch (Exception e) {
            log.error("请求金蝶云星空收付款用途接口异常", e);
            throw new OpenApiArgumentException("请求金蝶云星空收付款用途接口异常");
        }
    }

    private Object getThirdEmployeeId(K3CloudApiClient client, Object creatorId, String companyId) {
        // 根据金蝶用户ID获取手机号
        KingdeeBillQueryDTO req = new KingdeeBillQueryDTO();
        req.setFormId(SEC_USER);
        req.setFieldKeys("FPhone");
        req.setFilterString("FUserID='" + creatorId + "'");
        String phone = "";
        try {
            log.info("K3CloudApiClient executeBillQuery req = {}", JsonUtils.toJson(req));
            List<List<Object>> lists = client.executeBillQuery(JsonUtils.toJson(req));
            log.info("K3CloudApiClient executeBillQuery resp = {}", JsonUtils.toJson(lists));
            if (Func.isNotEmpty(lists)) {
                List<Object> objects = lists.get(0);
                if (Func.isNotEmpty(objects)) {
                    phone = (String) objects.get(0);
                }
            }
        } catch (Exception e) {
            log.error("请求金蝶云星空创建人接口异常", e);
            throw new OpenApiArgumentException("请求金蝶云星空创建人接口异常");
        }
        // 金蝶手机号不存在
        if (Func.isBlank(phone)) {
            log.error("查询金蝶云星空创建人手机号不存在 creatorId={}", creatorId);
            throw new OpenApiArgumentException("查询金蝶云星空创建人手机号不存在");
        }
        // 根据手机号获取三方用户ID
        int pageIndex = 1;
        int pageSize = 100;
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json");
        headers.add("appId", companyId);
        String reqUrl = tigerHost + EMPLOYEE_LIST;
        Map<String, Object> reqEmployee = new HashMap<>(8);
        reqEmployee.put("page_index", pageIndex);
        reqEmployee.put("page_size", pageSize);
        // 获取总页数
        Integer totalPages = getTotalPages(reqUrl, headers, reqEmployee);
        String thirdId = "";
        // 分页查询员工信息 匹配手机号 获取三方ID
        while (Func.isBlank(thirdId) && pageIndex <= totalPages) {
            // page_index从1开始 页数递增1, 2, 3...
            reqEmployee.put("page_index", pageIndex);
            reqEmployee.put("page_size", pageSize);
            thirdId = matchThirdIdByPhone(reqUrl, headers, reqEmployee, phone);
            pageIndex++;
        }
        if (Func.isBlank(thirdId)) {
            log.error("查询uc获取创建人三方ID不存在 phone={}", phone);
            throw new OpenApiArgumentException("查询uc获取创建人三方ID不存在");
        }
        return thirdId;
    }

    private Integer getTotalPages(String reqUrl, HttpHeaders headers, Map<String, Object> reqEmployee) {
        try {
            String resp = restHttpUtils.postJson(reqUrl, headers, JsonUtils.toJson(reqEmployee));
            ResultVo result = JsonUtils.toObj(resp, ResultVo.class);
            if (result.getCode() != 0) {
                log.error("查询员工列表失败 resultVo = {}", JsonUtils.toJson(result));
                throw new OpenApiArgumentException("查询员工列表失败" + JsonUtils.toJson(result));
            }
            OpenEmployeeListResDTO employeeInfo = JsonUtils.toObj(JsonUtils.toJson(result.getData()), OpenEmployeeListResDTO.class);
            if (Func.isNotNull(employeeInfo)) {
                return employeeInfo.getTotalPages();
            }
        } catch (Exception e) {
            log.error("查询uc获取创建人三方ID异常", e);
            throw new OpenApiArgumentException("查询uc获取创建人三方ID异常");
        }
        return 1;
    }

    private String matchThirdIdByPhone(String reqUrl, HttpHeaders headers, Map<String, Object> reqEmployee, String phone) {
        try {
            String resp = restHttpUtils.postJson(reqUrl, headers, JsonUtils.toJson(reqEmployee));
            ResultVo result = JsonUtils.toObj(resp, ResultVo.class);
            if (result.getCode() != 0) {
                log.error("查询员工列表失败 resultVo = {}", JsonUtils.toJson(result));
                throw new OpenApiArgumentException("查询员工列表失败" + JsonUtils.toJson(result));
            }
            OpenEmployeeListResDTO employeeInfo = JsonUtils.toObj(JsonUtils.toJson(result.getData()), OpenEmployeeListResDTO.class);
            if (Func.isNotNull(employeeInfo)) {
                List<OpenEmployeeListResDTO.BaseEmployeeDTO> employees = employeeInfo.getEmployees();
                if (Func.isNotEmpty(employees)) {
                    List<OpenEmployeeListResDTO.BaseEmployeeDTO> collect = employees.stream().filter(e -> Func.equals(e.getPhone(), phone)).collect(Collectors.toList());
                    if (Func.isNotEmpty(collect)) {
                        OpenEmployeeListResDTO.BaseEmployeeDTO baseEmployeeDTO = collect.get(0);
                        return baseEmployeeDTO.getThirdId();
                    }
                }
            }
        } catch (Exception e) {
            log.error("查询uc获取创建人三方ID异常", e);
            throw new OpenApiArgumentException("查询uc获取创建人三方ID异常");
        }
        return "";
    }

    @Data
    public static class OpenEmployeeListResDTO {
        private List<BaseEmployeeDTO> employees;
        @JsonProperty("total_count")
        private Integer totalCount;
        @JsonProperty("total_pages")
        private Integer totalPages;
        @JsonProperty("page_index")
        private Integer pageIndex;
        @JsonProperty("page_size")
        private Integer pageSize;

        @Data
        public static class BaseEmployeeDTO {
            @JsonProperty("id")
            private String id;
            @JsonProperty("third_id")
            private String thirdId;
            @JsonProperty("name")
            private String name;
            @JsonProperty("dept_id")
            private String deptId;
            @JsonProperty("third_dept_id")
            private String thirdDeptId;
            @JsonProperty("dept_name")
            private String deptName;
            @JsonProperty("phone")
            private String phone;
            @JsonProperty("manager")
            private Boolean manager;
            @JsonProperty("state")
            private Integer state;
        }
    }

    private void invokePaymentCreate(String companyId, String formId, String accountId, String accountType,
                                     Object thirdId, Object thirdEmployeeId, BigDecimal amount, Object use, Object supplierThirdId, Object supplierThirdAccountId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.add("Content-Type", "application/json");
            headers.add("appId", companyId);
            String reqUrl = tigerHost + PAYMENT_CREATE;
            Map<String, Object> req = new HashMap<>(8);
            Map<String, Object> order = new HashMap<>(16);
            order.put("form_id", formId);
            order.put("name", "对公付款单");
            order.put("account_id", accountId);
            order.put("account_type", accountType);
            order.put("third_id", thirdId);
            order.put("third_employee_id", thirdEmployeeId);
            order.put("amount", amount);
            order.put("use", use);
            Map<String, Object> supplier = new HashMap<>(4);
            supplier.put("third_id", supplierThirdId);
            supplier.put("third_account_id", supplierThirdAccountId);
            order.put("supplier", supplier);
            req.put("order", order);
            String resp = restHttpUtils.postJson(reqUrl, headers, JsonUtils.toJson(req));
            ResultVo result = JsonUtils.toObj(resp, ResultVo.class);
            if (result.getCode() != 0) {
                log.error("创建自定义付款单失败 resultVo = {}", JsonUtils.toJson(result));
                throw new OpenApiArgumentException("创建自定义付款单失败" + JsonUtils.toJson(result));
            }
        } catch (Exception e) {
            log.error("创建自定义付款单失败", e);
            throw new OpenApiArgumentException("创建自定义付款单异常" + e.getMessage());
        }
    }

}
