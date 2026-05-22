package com.factory.chatbot_service.service;


import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.model.ExpressionType;
import software.amazon.awssdk.services.s3.model.InputSerialization;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.OutputSerialization;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.model.SelectObjectContentRequest;
import software.amazon.awssdk.services.s3.model.SelectObjectContentResponseHandler;

@Service
public class S3Service {
    private final S3Client s3Client;
    public S3Service(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    /**
     * 특정 폴더(Prefix) 내의 Parquet 파일을 찾아 내용을 JSON 문자열로 추출합니다.
     */
    public String readParquetAsJson(String bucketName, String prefix) {
        System.out.println("[DEBUG] S3Service : readParquetAsJson() called");
        System.out.println("[DEBUG] bucketName : " + bucketName);
        System.out.println("[DEBUG] prefix : " + prefix);

        try {
            // 해당 경로 하위의 파일 목록 조회
            ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(prefix)
                .maxKeys(1) // 가장 첫 번째 파일 1개만 매핑
                .build();

            ListObjectsV2Response listResponse = s3Client.listObjectsV2(listRequest);

            if (listResponse.contents().isEmpty()) {
                return "<error>지정한 경로에 파일이 존재하지 않습니다.</error>";
            }

            // 난수(UUID)가 포함된 실제 Parquet 파일의 풀 키(Key) 획득
            String actualKey = listResponse.contents().get(0).key();
            System.out.println("[S3 데이터 읽기] 대상 파일 확정: " + actualKey);

            // 2. S3 Select 요청 구성 (Parquet -> JSON 변환 연산 위임)
            SelectObjectContentRequest selectRequest = SelectObjectContentRequest.builder()
                .bucket(bucketName)
                .key(actualKey)
                .expression("SELECT * FROM S3Object") // 전체 데이터 조회 SQL
                .expressionType(ExpressionType.SQL)
                .inputSerialization(InputSerialization.builder()
                    .parquet(ParquetInputSerialization.builder().build()) // 입력: Parquet
                    .build())
                .outputSerialization(OutputSerialization.builder()
                    .json(JsonOutputSerialization.builder().build()) // 출력: JSON
                    .build())
                .build();

            // 3. 데이터 스트림 스트리밍 처리 및 결합
            List<String> jsonRecords = new ArrayList<>();

            s3Client.selectObjectContent(selectRequest, SelectObjectContentResponseHandler.builder()
                .onRecords(event -> {
                    // S3가 파싱해서 보내준 JSON 행 바이트 배열을 String으로 변환
                    String payload = event.payload().asUtf8String();
                    jsonRecords.add(payload);
                })
                .build());

            // 조회된 결괏값 결합
            String resultJson = String.join("\n", jsonRecords);

            System.out.println("[S3 데이터 읽기 성공] 데이터 추출 완료");
            return resultJson;

        } catch (Exception e) {
            System.err.println("[S3 데이터 읽기 실패] 에러 발생: " + e.getMessage());
            e.printStackTrace();
            return "<error>S3 데이터를 읽는 중 문제가 발생했습니다: " + e.getMessage() + "</error>";
        }
    }





    /**
     * 지정한 S3 버킷 내부의 파일(Key)이 실제로 존재하고 접근 가능한지 확인하는 메소드
     */
    public void testS3Connection(String bucketName, String prefix) {
        System.out.println("[DEBUG] S3Service : testS3Connection() called");
        System.out.println("[DEBUG] bucketName : " + bucketName);
        System.out.println("[DEBUG] prefix : " + prefix);

        try {
            ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(prefix)
                .maxKeys(5) // 상위 5개만 확인
                .build();

            ListObjectsV2Response listResponse = s3Client.listObjectsV2(listRequest);

            System.out.println("[S3 성공] 버킷 연결에 성공했습니다.");
            System.out.println("조회된 파일 개수: " + listResponse.contents().size());

            for (S3Object s3Object : listResponse.contents()) {
                System.out.println(" - 발견된 파일: " + s3Object.key() + " (크기: " + s3Object.size() + " bytes)");
            }
        } catch (Exception e) {
            System.err.println("[S3 실패] 에러 발생: " + e.getMessage());
            e.printStackTrace();
        }
    }

}
