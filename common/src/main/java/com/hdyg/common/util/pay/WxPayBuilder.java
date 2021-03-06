package com.hdyg.common.util.pay;

import android.content.Context;

import com.google.gson.Gson;
import com.hdyg.common.bean.WxOpenBean;
import com.hdyg.common.bean.WxPayBean;
import com.hdyg.common.bean.WxShareBean;
import com.hdyg.common.httpUtil.okhttp.CallBackUtil;
import com.hdyg.common.httpUtil.okhttp.OkhttpUtil;
import com.hdyg.common.util.LogUtils;
import com.hdyg.common.util.StringUtil;
import com.hdyg.common.util.ThreadUtil;
import com.hdyg.common.util.ToastUtil;
import com.tencent.mm.opensdk.constants.ConstantsAPI;
import com.tencent.mm.opensdk.modelbase.BaseResp;
import com.tencent.mm.opensdk.modelbiz.WXLaunchMiniProgram;
import com.tencent.mm.opensdk.modelmsg.SendAuth;
import com.tencent.mm.opensdk.modelmsg.SendMessageToWX;
import com.tencent.mm.opensdk.modelmsg.WXMediaMessage;
import com.tencent.mm.opensdk.modelmsg.WXWebpageObject;
import com.tencent.mm.opensdk.modelpay.PayReq;
import com.tencent.mm.opensdk.openapi.IWXAPI;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.json.JSONObject;

import java.lang.ref.WeakReference;

import okhttp3.Call;

/**
 * @author CZB
 * @describe 微信工具 授权/分享/支付/打开小程序
 * @time 2020/4/9 11:42
 */
public class WxPayBuilder {

    private Context mContext;
    private String mAppId;
    private WxPayBean mBean;
    private WxShareBean mShareBean;
    private WxOpenBean mOpenBean;
    private OpenCallback mOpenCallback;
    private PayCallback mPayCallback;
    private ShareCallback mShareCallback;
    private AuthCallback mAuthCallback;
    //SendMessageToWX.Req.WXSceneSession    对话
    //SendMessageToWX.Req.WXSceneTimeline   朋友圈
    //SendMessageToWX.Req.WXSceneFavorite   收藏
    private int mTargetScene = SendMessageToWX.Req.WXSceneSession;//默认分享到对话

    public WxPayBuilder(Context context, String appId) {
        mContext = context;
        mAppId = appId;
        WxApiWrapper.getInstance().setAppID(appId);
        EventBus.getDefault().register(this);
    }

    public WxPayBuilder setPayParam(WxPayBean bean) {
        mBean = bean;
        return this;
    }

    public WxPayBuilder setPayCallback(PayCallback callback) {
        mPayCallback = new WeakReference<>(callback).get();
        return this;
    }

    public WxPayBuilder setShareParam(WxShareBean bean) {
        mShareBean = bean;
        return this;
    }

    public WxPayBuilder setShareType(int shareType) {
        mTargetScene = shareType;
        return this;
    }

    public WxPayBuilder setShareCallback(ShareCallback callback) {
        mShareCallback = new WeakReference<>(callback).get();
        return this;
    }

    public WxPayBuilder setOpenParam(WxOpenBean bean) {
        mOpenBean = bean;
        return this;
    }

    public WxPayBuilder setOpenCallback(OpenCallback callback) {
        mOpenCallback = new WeakReference<>(callback).get();
        return this;
    }

    public WxPayBuilder setAuthCallback(AuthCallback callback) {
        mAuthCallback = new WeakReference<>(callback).get();
        return this;
    }

    /**
     * 授权
     */
    public void auth() {
        SendAuth.Req req = new SendAuth.Req();
        req.scope = "snsapi_userinfo";
        req.state = "auth_apk";
        IWXAPI wxApi = WxApiWrapper.getInstance().getWxApi();
        if (wxApi == null) {
            ToastUtil.show("授权失败");
            return;
        }
        boolean result = wxApi.sendReq(req);
        if (!result) {
            ToastUtil.show("授权失败");
        }
    }

    /**
     * 支付
     */
    public void pay() {
        PayReq request = new PayReq();
        LogUtils.d("发送支付的appid==>" + mBean.appid);
        request.appId = mBean.appid;
        request.partnerId = mBean.partnerid;
        request.prepayId = mBean.prepayid;
        request.packageValue = mBean.packageStr;
        request.nonceStr = mBean.noncestr;
        request.timeStamp = mBean.timestamp;
        request.sign = mBean.sign;
        IWXAPI wxApi = WxApiWrapper.getInstance().getWxApi();
        if (wxApi == null) {
            ToastUtil.show("支付失败");
            return;
        }
        boolean result = wxApi.sendReq(request);
        if (!result) {
            ToastUtil.show("支付失败");
        }
    }

    /**
     * 分享
     */
    public void share() {
        ThreadUtil.runOnSubThread(() -> {
            //初始化一个WXWebpageObject，填写url
            WXWebpageObject webpage = new WXWebpageObject();
            webpage.webpageUrl = mShareBean.url;
            //用 WXWebpageObject 对象初始化一个 WXMediaMessage 对象
            WXMediaMessage msg = new WXMediaMessage(webpage);
            msg.title = mShareBean.title;
            msg.description = mShareBean.des;
            msg.thumbData = StringUtil.getHtmlByteArray(mShareBean.logo);
            //构造一个Req
            SendMessageToWX.Req req = new SendMessageToWX.Req();
            req.transaction = buildTransaction("webpage");
            req.message = msg;
            req.scene = mTargetScene;
            req.userOpenId = mShareBean.openid;
            //调用api接口，发送数据到微信
            IWXAPI wxApi = WxApiWrapper.getInstance().getWxApi();
            wxApi.sendReq(req);
        });

    }

    /**
     * 打开小程序
     */
    public void openMiniProgram(){
        WXLaunchMiniProgram.Req req = new WXLaunchMiniProgram.Req();
        req.userName = mOpenBean.userName; // 填小程序原始id
        req.path = mOpenBean.path;                  ////拉起小程序页面的可带参路径，不填默认拉起小程序首页，对于小游戏，可以只传入 query 部分，来实现传参效果，如：传入 "?foo=bar"。
        req.miniprogramType = mOpenBean.miniprogramType;// 开发版1，体验版2和正式版0
        //调用api接口，发送数据到微信
        IWXAPI wxApi = WxApiWrapper.getInstance().getWxApi();
        wxApi.sendReq(req);
    }

    private String buildTransaction(final String type) {
        return (type == null) ? String.valueOf(System.currentTimeMillis()) : type + System.currentTimeMillis();
    }


    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onPayResponse(BaseResp resp) {
        LogUtils.e("resp---微信回调---->" + resp.errCode);
        String result;
        if (mPayCallback != null) {
            //支付
            if (resp.getType() == ConstantsAPI.COMMAND_PAY_BY_WX) {
                switch (resp.errCode) {
                    case BaseResp.ErrCode.ERR_OK://成功
                        result = "支付成功";
                        mPayCallback.onSuccess();
                        break;
                    case BaseResp.ErrCode.ERR_COMM://签名错误、未注册APPID、项目设置APPID不正确、注册的APPID与设置的不匹配、其他异常等
                        result = "签名错误";
                        mPayCallback.onFailed(result);
                        break;
                    case BaseResp.ErrCode.ERR_USER_CANCEL:
                        result = "支付取消";
                        mPayCallback.onFailed(result);
                        break;
                    default:
                        result = "支付失败";
                        mPayCallback.onFailed(result);
                        break;
                }
                mPayCallback = null;
            }
        }
        if (mAuthCallback != null) {
            //授权
            if (resp.getType() == ConstantsAPI.COMMAND_SENDAUTH) {
                switch (resp.errCode) {
                    case BaseResp.ErrCode.ERR_OK://成功
                        result = "授权成功";
                        SendAuth.Resp authResp = (SendAuth.Resp) resp;
                        final String code = authResp.code;
                        mAuthCallback.onSuccess(code);
                        break;
                    case BaseResp.ErrCode.ERR_COMM://签名错误、未注册APPID、项目设置APPID不正确、注册的APPID与设置的不匹配、其他异常等
                        result = "签名错误";
                        mAuthCallback.onFailed(result);
                        break;
                    case BaseResp.ErrCode.ERR_USER_CANCEL:
                        result = "授权取消";
                        mAuthCallback.onFailed(result);
                        break;
                    default:
                        result = "授权失败";
                        mAuthCallback.onFailed(result);
                        break;
                }
                mAuthCallback = null;
            }
        }
        if (mShareCallback != null) {
            //分享
            if (resp.getType() == ConstantsAPI.COMMAND_SENDMESSAGE_TO_WX) {
                switch (resp.errCode) {
                    case BaseResp.ErrCode.ERR_OK://成功
                        result = "分享成功";
                        mShareCallback.onShareSuccess();
                        break;
                    case BaseResp.ErrCode.ERR_COMM://签名错误、未注册APPID、项目设置APPID不正确、注册的APPID与设置的不匹配、其他异常等
                        result = "签名错误";
                        mShareCallback.onShareFailed(result);
                        break;
                    case BaseResp.ErrCode.ERR_USER_CANCEL:
                        result = "分享取消";
                        mShareCallback.onShareCancle(result);
                        break;
                    default:
                        result = "分享失败";
                        mShareCallback.onShareFailed(result);
                        break;
                }
                mShareCallback = null;
            }
        }
        if (mOpenCallback != null) {
            //小程序回退到APP
            if (resp.getType() == ConstantsAPI.COMMAND_LAUNCH_WX_MINIPROGRAM) {
                WXLaunchMiniProgram.Resp launchMiniProResp = (WXLaunchMiniProgram.Resp) resp;
                String extraData =launchMiniProResp.extMsg;
                mOpenCallback.onSuccess(extraData);
                mOpenCallback = null;
            }
        }
        mContext = null;
        EventBus.getDefault().unregister(this);
    }

    //获取用户信息
    private void getUserInfo(String code){
        //获取授权
        String mSecret = "";
        String loginUrl = "https://api.weixin.qq.com/sns/oauth2/access_token?appid="+mAppId+"&secret="+mSecret+"&code="+code+"&grant_type=authorization_code";
        OkhttpUtil.okHttpGet(loginUrl, new CallBackUtil.CallBackString() {
            @Override
            public void onFailure(Call call, Exception e) {
                LogUtils.e("AccessCode Fail==>"+e.getMessage());
            }

            @Override
            public void onResponse(String response) {
                try {
                    JSONObject jsonObject = new JSONObject(response);
                    String access = jsonObject.getString("access_token");
                    String openId = jsonObject.getString("openid");
                    //获取个人信息
                    String getUserInfo = "https://api.weixin.qq.com/sns/userinfo?access_token=" + access + "&openid=" + openId;
                    OkhttpUtil.okHttpGet(getUserInfo, new CallBackString() {
                        @Override
                        public void onFailure(Call call, Exception e) {
                            LogUtils.e("UserInfo Fail==>"+e.getMessage());
                        }

                        @Override
                        public void onResponse(String response) {
                            LogUtils.d("UserInfo Success==>"+response);
                        }
                    });
                }catch (Exception e){
                    LogUtils.e("GetUserInfo Exception==>"+new Gson().toJson(e));
                }

            }
        });
    }


}
