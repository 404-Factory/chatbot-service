package com.factory.chatbot_service.controller;

import com.factory.chatbot_service.service.S3Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test")
public class TestController {

    private final S3Service s3Service;

    // application.yaml에 등록한 버킷명을 가져옵니다.
    @Value("${chatbot.s3.bucket-name}")
    private String bucketName;

    public TestController(S3Service s3Service) {
        this.s3Service = s3Service;
    }



    /**
     * <p>S3 연결 및 파일 리스트 조회 테스트 엔드포인트</p>
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

    /**
     * <p> Parquet 파일 내부 데이터를 JSON 텍스트로 읽어오는 테스트 엔드포인트 </p>
     * 호출 URL: GET http://localhost:8080/api/test/parquet
     */
    @GetMapping("/parquet")
    public String testReadParquet(@RequestParam(required = false) String prefix) {
        if (prefix == null || prefix.isBlank()) {
            prefix = "summary-data/daily/year=2026/month=5/factoryId=FAB-SEMICONDUCTOR-002/processStage=CLEANING/";
        }

        // S3 Select를 통해 파싱된 텍스트 반환받기
        return s3Service.readParquetAsJson(bucketName, prefix);
    }


    /**
     * Raw JSON 파일 내부 데이터를 그대로 읽어오는 테스트 엔드포인트
     * 호출 URL: GET http://localhost:8080/api/test/raw
     */
    @GetMapping("/raw")
    public String testReadRawJson(@RequestParam(required = false) String prefix) {
        if (prefix == null || prefix.isBlank()) {
            // 이미지에서 확인된 2026년 5월 22일 자 LINE-02 Raw 데이터 경로
            prefix = "FAB-SEMICONDUCTOR-002/LINE-02/2026/05/22/";
        }

        // S3Service를 통해 순수 JSON 파일 내용 반환
        return s3Service.readRawJson(bucketName, prefix);
    }

}