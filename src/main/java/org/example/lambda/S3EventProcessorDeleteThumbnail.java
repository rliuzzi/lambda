package org.example.lambda;

import java.io.IOException;
import java.net.URLDecoder;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.event.S3EventNotification.S3EventNotificationRecord;

public class S3EventProcessorDeleteThumbnail implements RequestHandler<S3Event, String> {

	public String handleRequest(S3Event s3event, Context context) {
		
		try {
			
			S3EventNotificationRecord record = s3event.getRecords().get(0);
			String srcBucket = record.getS3().getBucket().getName();
			
			// Object key may have spaces or unicode non-ASCII characters.
			String srcKey = record.getS3().getObject().getKey().replace('+', ' ');
			srcKey = URLDecoder.decode(srcKey, "UTF-8");
			
			AmazonS3 s3Client = new AmazonS3Client();

			String dstBucket = srcBucket + "resized";
			String dstKey = Target.deduceTarget(srcKey);

			// Delete from S3 destination bucket
			System.out.println("Deleting from: " + dstBucket + "/" + dstKey);
			s3Client.deleteObject(dstBucket, dstKey);
			System.out.println("Successfully Deleted " + dstBucket + "/" + dstKey);
			return "Ok";
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
