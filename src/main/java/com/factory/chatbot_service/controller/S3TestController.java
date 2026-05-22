package com.factory.chatbot_service.controller;

import com.factory.chatbot_service.service.S3Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test")
public class S3TestController {

    private final S3Service s3Service;

    // application.yaml에 등록한 버킷명을 가져옵니다.
    @Value("${chatbot.s3.bucket-name}")
    private String bucketName;

    public S3TestController(S3Service s3Service) {
        this.s3Service = s3Service;
    }

    /**
     * S3 연결 및 파일 리스트 조회 테스트 엔드포인트
     * 호출 URL: GET http://localhost:8080/api/test/s3
     */
    @GetMapping("/s3")
    public String testS3Connection(@RequestParam(required = false) String prefix) {

        // 파라미터가 비어있을 경우, 스냅샷에 나온 2026년 5월 CLEANING 공정 경로를 기본값으로 사용
        if (prefix == null || prefix.isBlank()) {
            prefix = "summary-data/daily/year=2026/month=5/factoryId=FAB-SEMICONDUCTOR-002/processStage=CLEANING/";
        }

        System.out.println("\n============ [테스트 엔드포인트 호출] ============");
        System.out.println("대상 버킷: " + bucketName);
        System.out.println("대상 경로(Prefix): " + prefix);
        System.out.println("==================================================");

        // 서비스의 조회 로직 실행
        s3Service.testS3Connection(bucketName, prefix);

        return "S3 연결 테스트 조회가 완료되었습니다. 인텔리제이(또는 STS) 콘솔 로그의 '[S3 성공]' 혹은 '[S3 실패]' 메시지를 확인하세요!";
    }
}