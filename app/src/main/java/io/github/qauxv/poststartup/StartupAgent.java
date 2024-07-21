/*
 * QAuxiliary - An Xposed module for QQ/TIM
 * Copyright (C) 2019-2024 QAuxiliary developers
 * https://github.com/cinit/QAuxiliary
 *
 * This software is an opensource software: you can redistribute it
 * and/or modify it under the terms of the General Public License
 * as published by the Free Software Foundation; either
 * version 3 of the License, or any later version as published
 * by QAuxiliary contributors.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the General Public License for more details.
 *
 * You should have received a copy of the General Public License
 * along with this software.
 * If not, see
 * <https://github.com/cinit/QAuxiliary/blob/master/LICENSE.md>.
 */

package io.github.qauxv.poststartup;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.github.qauxv.loader.hookapi.IHookBridge;
import io.github.qauxv.loader.hookapi.ILoaderInfo;
import io.github.qauxv.util.IoUtils;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.lsposed.hiddenapibypass.HiddenApiBypass;

@Keep
public class StartupAgent {

    private static boolean sInitialized = false;

    private StartupAgent() {
        throw new AssertionError("No instance for you!");
    }

    @Keep
    public static void startup(
            @NonNull String modulePath,
            @NonNull ApplicationInfo appInfo,
            @NonNull ILoaderInfo loaderInfo,
            @NonNull ClassLoader hostClassLoader,
            @Nullable IHookBridge hookBridge
    ) {
        if (sInitialized) {
            return;
        }
        sInitialized = true;
        if (io.github.qauxv.R.string.res_inject_success >>> 24 == 0x7f) {
            throw new AssertionError("package id must NOT be 0x7f, reject loading...");
        }
        if ("true".equals(System.getProperty(StartupAgent.class.getName()))) {
            android.util.Log.e("QAuxv", "Error: QAuxiliary reloaded??");
            // I don't know... What happened?
            return;
        }
        StartupInfo.modulePath = modulePath;
        StartupInfo.loaderInfo = loaderInfo;
        StartupInfo.hookBridge = hookBridge;
        // bypass hidden api
        ensureHiddenApiAccess();
        // we want context
        Application baseApp = getApplicationByActivityThread();
        if (baseApp == null) {
            if (hookBridge == null) {
                throw new UnsupportedOperationException("neither base application nor hook bridge found");
            }
            StartupHook.getInstance().initializeBeforeAppCreate(hostClassLoader);
        } else {
            Context ctx = getBaseApplicationImpl(hostClassLoader);
            if (ctx == null) {
                throw new AssertionError("getBaseApplicationImpl() == null but getApplicationByActivityThread() != null");
            }
            StartupHook.getInstance().initializeAfterAppCreate(ctx);
        }
    }

    public static Context getBaseApplicationImpl(@NonNull ClassLoader classLoader) {
        Context app;
        try {
            Class<?> clz = classLoader.loadClass("com.tencent.common.app.BaseApplicationImpl");
            Field fsApp = null;
            for (Field f : clz.getDeclaredFields()) {
                if (f.getType() == clz) {
                    fsApp = f;
                    break;
                }
            }
            if (fsApp == null) {
                throw new UnsupportedOperationException("field BaseApplicationImpl.sApplication not found");
            }
            app = (Context) fsApp.get(null);
            return app;
        } catch (ReflectiveOperationException e) {
            android.util.Log.e("QAuxv", "getBaseApplicationImpl: failed", e);
            throw IoUtils.unsafeThrow(e);
        }
    }

    @Nullable
    public static Application getApplicationByActivityThread() {
        try {
            Class<?> kActivityThread = Class.forName("android.app.ActivityThread");
            Method mGetApplication = kActivityThread.getDeclaredMethod("currentApplication");
            return (Application) mGetApplication.invoke(null);
        } catch (ReflectiveOperationException e) {
            android.util.Log.e("QAuxv", "getApplicationByActivityThread: failed", e);
            throw IoUtils.unsafeThrow(e);
        }
    }

    private static void ensureHiddenApiAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !isHiddenApiAccessible()) {
            android.util.Log.w("QAuxv", "Hidden API access not accessible, SDK_INT is " + Build.VERSION.SDK_INT);
            HiddenApiBypass.setHiddenApiExemptions("L");
        }
    }

    @SuppressLint({"BlockedPrivateApi", "PrivateApi"})
    public static boolean isHiddenApiAccessible() {
        Class<?> kContextImpl;
        try {
            kContextImpl = Class.forName("android.app.ContextImpl");
        } catch (ClassNotFoundException e) {
            return false;
        }
        Field mActivityToken = null;
        Field mToken = null;
        try {
            mActivityToken = kContextImpl.getDeclaredField("mActivityToken");
        } catch (NoSuchFieldException ignored) {
        }
        try {
            mToken = kContextImpl.getDeclaredField("mToken");
        } catch (NoSuchFieldException ignored) {
        }
        return mActivityToken != null || mToken != null;
    }

}
