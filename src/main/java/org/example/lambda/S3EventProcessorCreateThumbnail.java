package org.example.lambda;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
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

public class S3EventProcessorCreateThumbnail implements RequestHandler<S3Event, String> {
	private static final float MAX_WIDTH = 250;
	private static final float MAX_HEIGHT = 250;
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
			//String dstKey = srcKey.substring(srcKey.lastIndexOf("/") > 0 ? srcKey.lastIndexOf("/")  : 0, srcKey.lastIndexOf(".") > 0 ? srcKey.lastIndexOf(".") : srcKey.length()-1);
			String dstKey = "resized" + srcKey;
			
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
			int srcHeight = srcImage.getHeight();
			int srcWidth = srcImage.getWidth();
			
			// Infer the scaling factor to avoid stretching the image
			// unnaturally
			float scalingFactor = Math.min(MAX_WIDTH / srcWidth, MAX_HEIGHT	/ srcHeight);
			int width = (int) (scalingFactor * srcWidth);
			int height = (int) (scalingFactor * srcHeight);
			
			BufferedImage resizedImage;
			if(srcWidth != MAX_WIDTH && srcHeight != MAX_HEIGHT){
				resizedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
				Graphics2D g = resizedImage.createGraphics();
				// Fill with white before applying semi-transparent (alpha) images
				g.setPaint(Color.white);
				g.fillRect(0, 0, width, height);
				// Simple bilinear resize
				// If you want higher quality algorithms, check this link:
				// https://today.java.net/pub/a/today/2007/04/03/perils-of-image-getscaledinstance.html
				g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
				g.drawImage(srcImage, 0, 0, width, height, null);
				g.dispose();
				
				// Re-encode image to target format
				
			} else {
				resizedImage = srcImage;
			}
			
			ByteArrayOutputStream os = new ByteArrayOutputStream();
			ImageIO.write(resizedImage, imageType, os);
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
			
			//meta.setHeader("x-amz-copy-source",  srcBucket + "/" + srcKey);
			
			/** Example Custom ACL usage
			AccessControlList acl = new AccessControlList();
			acl.grantPermission(new CanonicalGrantee("d25639fbe9c19cd30a4c0f43fbf00e2d3f96400a9aa8dabfbbebe1906Example"), Permission.ReadAcp);
			acl.grantPermission(GroupGrantee.AllUsers, Permission.Read);
			acl.grantPermission(new EmailAddressGrantee("user@email.com"), Permission.WriteAcp);
			s3Client.putObject(new PutObjectRequest(dstBucket, dstKey, is, meta).withAccessControlList(acl));
			*/

			// Uploading to S3 destination bucket
			System.out.println("Writing to: " + dstBucket + "/" + dstKey);
			s3Client.putObject(new PutObjectRequest(dstBucket, dstKey, is, meta).withCannedAcl(CannedAccessControlList.PublicRead));
			//s3Client.putObject(new PutObjectRequest(dstBucket, dstKey, is, meta));
			System.out.println("Successfully resized " + srcBucket + "/" + srcKey + " and uploaded to " + dstBucket + "/" + dstKey);
			return "Ok";
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
