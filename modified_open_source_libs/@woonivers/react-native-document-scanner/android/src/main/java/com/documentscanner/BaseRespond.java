package com.documentscanner;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

public class BaseRespond implements Serializable {
    @SerializedName("Status")
    @Expose
    private int Status;

    @SerializedName("msg")
    @Expose
    private String msg;

    public int getStatus() {
        return Status;
    }

    public void setStatus(int status) {
        Status = status;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }
}