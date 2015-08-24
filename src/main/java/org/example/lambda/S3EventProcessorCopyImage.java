package org.example.lambda;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.event.S3EventNotification.S3EventNotificationRecord;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;

public class S3EventProcessorCopyImage implements RequestHandler<S3Event, String> {
	
	private final String JPG_TYPE = (String) "jpg";
	private final String JPG_MIME = (String) "image/jpeg";
	private final String PNG_TYPE = (String) "png";
	private final String PNG_MIME = (String) "image/png";
	private final String BMP_TYPE = (String) "bmp";
	private final String BMP_MIME = (String) "image/bmp";
	private final String GIF_TYPE = (String) "gif";
	private final String GIF_MIME = (String) "image/gif";

	public String handleRequest(S3Event s3event, Context context) {
		
		try {
			
			S3EventNotificationRecord record = s3event.getRecords().get(0);
			String srcBucket = record.getS3().getBucket().getName();
			
			// Object key may have spaces or unicode non-ASCII characters.
			String srcKey = record.getS3().getObject().getKey().replace('+', ' ');
			srcKey = URLDecoder.decode(srcKey, "UTF-8");

			String dstBucket = srcBucket + "resized";
			String dstKey = Target.deduceTarget(srcKey);
			
			// Sanity check: validate that source and destination are different
			// buckets.
			if (srcBucket.equals(dstBucket)) {
				System.out.println("Destination bucket must not match source bucket.");
				return "";
			}

			// Infer the image type.
			Matcher matcher = Pattern.compile(".*\\.([^\\.]*)").matcher(srcKey);
			if (!matcher.matches()) {
				System.out.println("Unable to infer image type for key " + srcKey);
				return "";
			}
			String imageType = matcher.group(1);
			if (!(JPG_TYPE.equals(imageType)) && !(PNG_TYPE.equals(imageType)) && 
					!(BMP_TYPE.equals(imageType)) && !(GIF_TYPE.equals(imageType))) {
				System.out.println("Skipping non-image " + srcKey);
				return "";
			}

			// Download the image from S3 into a stream
			AmazonS3 s3Client = new AmazonS3Client();
			S3Object s3Object = s3Client.getObject(new GetObjectRequest(srcBucket, srcKey));
			InputStream objectData = s3Object.getObjectContent();

			// Read the source image
			BufferedImage srcImage = ImageIO.read(objectData);
			ByteArrayOutputStream os = new ByteArrayOutputStream();
			ImageIO.write(srcImage, imageType, os);
			InputStream is = new ByteArrayInputStream(os.toByteArray());
			
			// Set Content-Length and Content-Type
			ObjectMetadata meta = new ObjectMetadata();
			meta.setContentLength(os.size());
			if (JPG_TYPE.equals(imageType)) {
				meta.setContentType(JPG_MIME);
			}
			if (PNG_TYPE.equals(imageType)) {
				meta.setContentType(PNG_MIME);
			}
			if (BMP_TYPE.equals(imageType)) {
				meta.setContentType(BMP_MIME);
			}
			if (GIF_TYPE.equals(imageType)) {
				meta.setContentType(GIF_MIME);
			}

			// Uploading to S3 destination bucket
			System.out.println("Writing to: " + dstBucket + "/" + dstKey);
			s3Client.putObject(new PutObjectRequest(dstBucket, dstKey, is, meta).withCannedAcl(CannedAccessControlList.PublicRead));
			System.out.println("Successfully written to " + dstBucket + "/" + dstKey);
			return "Ok";
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
