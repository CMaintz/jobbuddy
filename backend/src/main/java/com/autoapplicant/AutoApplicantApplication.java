package com.autoapplicant;

import com.autoapplicant.adapter.cli.OneShotCommand;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class AutoApplicantApplication {

    public static void main(String[] args) {
        OneShotCommand.exitWhenFinished(
                SpringApplication.run(AutoApplicantApplication.class, args), System::exit);
    }
}
