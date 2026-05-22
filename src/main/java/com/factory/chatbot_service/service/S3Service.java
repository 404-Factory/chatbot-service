package com.factory.chatbot_service.service;


import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3AsyncClient;
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
    private final S3AsyncClient s3AsyncClient;

    public S3Service(S3Client s3Client, S3AsyncClient s3AsyncClient) {
        this.s3Client = s3Client;
        this.s3AsyncClient = s3AsyncClient;
    }

    /**
     * 특정 폴더(Prefix) 내의 Parquet 파일을 찾아 내용을 JSON 문자열로 추출합니다.
     */
    public String readParquetAsJson(String bucketName, String prefix) {
        System.out.println("[DEBUG] S3Service : readParquetAsJson() called");
        System.out.println("[DEBUG] bucketName : " + bucketName);
        System.out.println("[DEBUG] prefix : " + prefix);

        try {
            // 1. 해당 경로 하위의 파일 목록 조회 (동기 클라이언트 사용 가능)
            ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(prefix)
                .maxKeys(1)
                .build();

            ListObjectsV2Response listResponse = s3Client.listObjectsV2(listRequest);

            if (listResponse.contents().isEmpty()) {
                return "<error>지정한 경로에 파일이 존재하지 않습니다.</error>";
            }

            String actualKey = listResponse.contents().get(0).key();
            System.out.println("[DEBUG] File directory : " + actualKey);

            // S3 Select 요청 구성 (v2 규격에 맞춘 올바른 모델 클래스 사용)
            SelectObjectContentRequest selectRequest = SelectObjectContentRequest.builder()
                .bucket(bucketName)
                .key(actualKey)
                .expression("SELECT * FROM S3Object")
                .expressionType(ExpressionType.SQL)
                .inputSerialization(InputSerialization.builder()
                    .parquet(ParquetInput.builder().build()) // ParquetInput 사용
                    .build())
                .outputSerialization(OutputSerialization.builder()
                    .json(JSONOutput.builder().build()) // JSONOutput 사용
                    .build())
                .build();

            // 데이터 스트림 스트리밍 처리 (Visitor 구조 적용)
            List<String> jsonRecords = new ArrayList<>();

            // 전용 Visitor 빌더를 사용해 Records 이벤트를 가로챕니다.
            SelectObjectContentResponseHandler.Visitor visitor = SelectObjectContentResponseHandler.Visitor.builder()
                .onRecords(r -> {
                    // r.payload()가 정상 작동하며 바이트 스트림을 UTF-8 문자열로 변환합니다.
                    String payload = r.payload().asUtf8String();
                    jsonRecords.add(payload);
                })
                .build();

            SelectObjectContentResponseHandler handler = SelectObjectContentResponseHandler.builder()
                .subscriber(visitor) // 빌드된 비지터를 구독자로 등록
                .build();

            // S3AsyncClient를 사용하여 S3 Select 연산을 수행하고 .join()으로 대기합니다.
            s3AsyncClient.selectObjectContent(selectRequest, handler).join();

            // 조회된 결괏값 결합
            String resultJson = String.join("\n", jsonRecords);

            System.out.println("[DEBUG] S3 데이터 추출 완료");
            return resultJson;

        } catch (Exception e) {
            System.err.println("[DEBUG] S3 데이터 읽기 실패: " + e.getMessage());
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



    /**
     * 특정 폴더(Prefix) 내의 일반 Raw JSON 파일을 찾아 내용을 문자열로 읽어옵니다.
     */
    public String readRawJson(String bucketName, String prefix) {
        System.out.println("S3Service : readRawJson() called");
        System.out.println("bucketName : " + bucketName);
        System.out.println("prefix : " + prefix);

        try {
            // 1. 해당 경로 하위의 Raw JSON 파일 목록 조회
            ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(prefix)
                .maxKeys(1) // 테스트를 위해 우선 가장 첫 번째 파일 1개만 지정
                .build();

            ListObjectsV2Response listResponse = s3Client.listObjectsV2(listRequest);

            if (listResponse.contents().isEmpty()) {
                return "<error>지정한 경로에 Raw JSON 파일이 존재하지 않습니다.</error>";
            }

            // 87개 파일 중 첫 번째 파일의 정확한 S3 Key(경로+파일명) 획득
            String actualKey = listResponse.contents().get(0).key();
            System.out.println("[DEBUG] S3 Raw data file : " + actualKey);

            // 2. S3 Object를 바이트 배열로 직접 다운로드 (순수 텍스트/JSON이므로 바로 읽기 가능)
            software.amazon.awssdk.core.BytesWrapper objectBytes = s3Client.getObjectAsBytes(
                software.amazon.awssdk.services.s3.model.GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(actualKey)
                    .build()
            );

            // 3. 바이트 데이터를 UTF-8 문자열(JSON 텍스트)로 변환
            String jsonContent = objectBytes.asUtf8String();

            System.out.println("[DEBUG] DATA Size : " + objectBytes.asByteArray().length + " bytes");
            return jsonContent;

        } catch (Exception e) {
            System.err.println("[DEBUG] DATA Read fail ERROR : " + e.getMessage());
            e.printStackTrace();
            return "<error>Raw JSON 데이터를 읽는 중 문제가 발생했습니다: " + e.getMessage() + "</error>";
        }
    }


}
