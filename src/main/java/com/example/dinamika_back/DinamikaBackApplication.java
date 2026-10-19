package com.example.dinamika_back;

import com.example.dinamika_back.features.report.FeatureCli;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DinamikaBackApplication {

	public static void main(String[] args) {
		// Команды обслуживания (print-features и др.): без веб-сервера и БД, процесс завершается кодом команды
		if (FeatureCli.isCommand(args)) {
			System.exit(FeatureCli.main(args));
		}
		SpringApplication.run(DinamikaBackApplication.class, args);
	}
  
}
