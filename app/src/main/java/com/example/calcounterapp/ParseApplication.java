package com.example.calcounterapp;

import android.app.Application;

import com.parse.Parse;
import com.parse.ParseObject;


public class ParseApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        //Register your parse model
        //ParseObject.registerSubclass(Post.class);

        Parse.initialize(new Parse.Configuration.Builder(this)
                .applicationId("6K6bHJCHR7en6u0D6ywlvoee5pqL4qFT0guVrCVH")
                .clientKey("q8U1CuLpUfazCIt0vAd5Nxe0hUOyHujNjsI4ogm3")
                .server("https://parseapi.back4app.com")
                .build()
        );
    }
}
