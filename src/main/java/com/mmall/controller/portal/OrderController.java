package com.mmall.controller.portal;

import com.alipay.api.AlipayApiException;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.demo.trade.config.Configs;
import com.google.common.collect.Maps;
import com.mmall.common.Constant;
import com.mmall.common.ResponseCode;
import com.mmall.common.ServerResponse;
import com.mmall.pojo.User;
import com.mmall.service.IOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.Iterator;
import java.util.Map;

@Controller
@RequestMapping("/order/")
public class OrderController {

    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);
    @Autowired
    private IOrderService iOrderService;


    /**
     * 创建订单
     * @param httpSession
     * @param shippingId
     * @return
     */
    @RequestMapping("create.do")
    @ResponseBody
    public ServerResponse create(HttpSession httpSession , Integer shippingId){
        User user = (User) httpSession.getAttribute(Constant.CURRENT_USER);
        if (null == user) {
            return ServerResponse.createByErrorCodeMessage(ResponseCode.NEED_LOGIN.getCode(), ResponseCode.NEED_LOGIN.getDesc());
        }

       return iOrderService.createOrder(user.getId() , shippingId);
    }


    /**
     * 取消订单
     * @param httpSession
     * @param orderNo
     * @return
     */
    @RequestMapping("cancel.do")
    @ResponseBody
    public ServerResponse cancel(HttpSession httpSession , Long orderNo){
        User user = (User) httpSession.getAttribute(Constant.CURRENT_USER);
        if (null == user) {
            return ServerResponse.createByErrorCodeMessage(ResponseCode.NEED_LOGIN.getCode(), ResponseCode.NEED_LOGIN.getDesc());
        }

        return iOrderService.cancel(user.getId() , orderNo);
    }

    /**
     * 获取购物车信息
     * @param httpSession
     * @return
     */
    @RequestMapping("get_order_cart_product.do")
    @ResponseBody
    public ServerResponse getOrderCartProduct(HttpSession httpSession){
        User user = (User) httpSession.getAttribute(Constant.CURRENT_USER);
        if (null == user) {
            return ServerResponse.createByErrorCodeMessage(ResponseCode.NEED_LOGIN.getCode(), ResponseCode.NEED_LOGIN.getDesc());
        }

        return iOrderService.getOrderCartProduct(user.getId());
    }

    /**
     * 获取订单详情
     * @param httpSession
     * @return
     */
    @RequestMapping("detail.do")
    @ResponseBody
    public ServerResponse detail(HttpSession httpSession , Long orderNo){
        User user = (User) httpSession.getAttribute(Constant.CURRENT_USER);
        if (null == user) {
            return ServerResponse.createByErrorCodeMessage(ResponseCode.NEED_LOGIN.getCode(), ResponseCode.NEED_LOGIN.getDesc());
        }

        return iOrderService.getOrderDetail(user.getId() , orderNo);
    }

    /**
     * 获取订单列表
     * @param httpSession
     * @return
     */
    @RequestMapping("list.do")
    @ResponseBody
    public ServerResponse list(HttpSession httpSession , @RequestParam(value = "pageNum" , defaultValue = "1") int pageNum ,
                               @RequestParam(value = "pageSize" , defaultValue = "10")  int pageSize){
        User user = (User) httpSession.getAttribute(Constant.CURRENT_USER);
        if (null == user) {
            return ServerResponse.createByErrorCodeMessage(ResponseCode.NEED_LOGIN.getCode(), ResponseCode.NEED_LOGIN.getDesc());
        }

        return iOrderService.getOrderList(user.getId() , pageNum , pageSize);
    }













    /**
     * HttpServletRequest对象代表客户端的请求，当客户端通过HTTP协议访问服务器时，HTTP请求头中的所有信息都封装在这个对象中，
     * 通过这个对象提供的方法，可以获得客户端请求的所有信息。
     *
     * @param httpSession
     * @param orderNo
     * @param httpServletRequest
     * @return
     */
    @RequestMapping("pay.do")
    @ResponseBody
    public ServerResponse pay(HttpSession httpSession, long orderNo, HttpServletRequest httpServletRequest) {
        User user = (User) httpSession.getAttribute(Constant.CURRENT_USER);
        if (null == user) {
            return ServerResponse.createByErrorCodeMessage(ResponseCode.NEED_LOGIN.getCode(), ResponseCode.NEED_LOGIN.getDesc());
        }

        String path = httpServletRequest.getSession().getServletContext().getRealPath("upload");

        return iOrderService.pay(orderNo, user.getId(), path);
    }

    @RequestMapping("alipay_callback.do")
    @ResponseBody
    public Object alipayCallback(HttpServletRequest httpServletRequest) {
        Map<String, String> params = Maps.newHashMap();

        Map requestParams = httpServletRequest.getParameterMap();
        for (Iterator iterator = requestParams.keySet().iterator(); iterator.hasNext(); ) {
            String name = (String) iterator.next();
            String[] values = (String[]) requestParams.get(name);
            String valueStr = "";
            for (int i = 0; i < values.length; i++) {
                valueStr = (i == values.length - 1) ? valueStr + values[i] : valueStr + values[i] + ",";
            }

            params.put(name, valueStr);

        }
        logger.info("支付宝回调,sign{},trade_status:{},参数:{}", params.get("sign"), params.get("trade_status"), params.toString());

        //验证回调正确性，还要避免重复性通知
        params.remove("sign_type");
        try {
            boolean alipayRSACheckV2 = AlipaySignature.rsaCheckV2(params , Configs.getAlipayPublicKey() , "utf-8" ,
                    Configs.getSignType());
            if (!alipayRSACheckV2) {
                return ServerResponse.createByErrorMessage("支付宝验证非法请求");
            }
        } catch (AlipayApiException e) {
            logger.error("支付宝验证回调异常" , e);
            e.printStackTrace();
        }

        //todo 验证其它参数

        ServerResponse serverResponse = iOrderService.aliCallback(params);
        if (serverResponse.isSuccess()) {
            return Constant.AlipayCallback.RESPONSE_SUCCESS;
        }
        return Constant.AlipayCallback.RESPONSE_FAILED;

    }

    @RequestMapping("query_order_pay_status.do")
    @ResponseBody
    public ServerResponse<Boolean> queryOrderPayStatus(HttpSession httpSession, long orderNo) {
        User user = (User) httpSession.getAttribute(Constant.CURRENT_USER);
        if (null == user) {
            return ServerResponse.createByErrorCodeMessage(ResponseCode.NEED_LOGIN.getCode(), ResponseCode.NEED_LOGIN.getDesc());
        }

        ServerResponse serverResponse =  iOrderService.queryOrderPayStatus(user.getId() , orderNo);
        if (serverResponse.isSuccess()) {
            return ServerResponse.createBySuccess(true);
        }
        return ServerResponse.createBySuccess(false);
    }




}
