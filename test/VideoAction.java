package com.cmsis.action;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import com.cmsis.beans.Video;
import com.cmsis.service.StorageService;
import com.cmsis.service.VideoService;
import my.img.JavaImgHandler;
import my.mvc.ActionException;
import my.mvc.Annotation;
import my.mvc.RequestContext;
import my.util.HumanReadableFilesize;
import my.util.Multimedia;

/**
 * 
 * @author Foxhu
 * 视频action类
 */
public class VideoAction extends BaseAction {
	public final static long MAX_FILE_SIZE = 50 * 1024 * 1024;//视频上传大小不能超过50M
	public final static long MAX_IMAGE_SIZE = 3 * 1024 * 1024;//图像文件大小不能超过3M
	/**
	 * 视频文件添加
	 * @param ctx
	 * @throws IOException
	 */
	
	public void add(RequestContext ctx) throws IOException{
		Video form = ctx.form(Video.class);//http请求参数映射到bean
		System.out.println("name="+ form.getName());
		System.out.println("introduce"+form.getIntroduce());
		if (StringUtils.isBlank(form.getName())){
			throw new ActionException("video_file_name_empty!");
		}
		java.io.File file = ctx.file("file");
		if (file == null){
			throw new ActionException("file empty!");
		}
		if (!Video.isLegalFile(file.getName())){
			throw new ActionException("file_illegal");
		}
		if(file.length() > MAX_FILE_SIZE ) {
			throw new ActionException("视频文件大小不能超过50M!");
		}
		String the_path = form.getPath();
		if(StringUtils.isNotBlank(the_path) && StorageService.VIDEOFILES.exist(the_path)){
			throw new ActionException("file exist!");
		}
		String uri = StringUtils.isBlank(the_path)?
				StorageService.VIDEOFILES.save(file):StorageService.VIDEOFILES.save(file,the_path);//文件存储
		form.setSize(HumanReadableFilesize.readableFilesize(file.length()));
		form.setPath(uri);
		form.setView_count(0);
		form.setUser(ctx.user().getId());
		//form.Save();
		long videoId = VideoService.addVideo(form);
		ctx.output_json(new String[]{"msg", "id"}, new Object[]{"", videoId});
	}
	
	/**
	 * 删除视频文件
	 * @param ctx
	 * @throws IOException
	 */
	public void delete(RequestContext ctx) throws IOException{
		long id = ctx.id();
		Video video = Video.INSTANCE.Get(id);
		if (video != null){
			StorageService.VIDEOFILES.delete(video.getPath());
		}
	}
	
	
	/**
	 * 批量删除视频
	 * @param ctx
	 * @throws IOException
	 */
	@Annotation.JSONOutputEnabled
	@Annotation.PostMethod
	@Annotation.UserRoleRequired()
	public void batchdel(RequestContext ctx) throws IOException {
		String videoids = ctx.param("id", "");
		String[] videoIdsArr = StringUtils.split(videoids, ',');
		for (String videoid : videoIdsArr) {
			Video video = Video.INSTANCE.Get(NumberUtils.toLong(videoid));
			VideoService.delVideo(video);
		}
	}

	/**
	 * 修改视频文件
	 * @param ctx
	 * @throws IOException
	 */
	public void edit(RequestContext ctx) throws IOException{
		Video form = ctx.form(Video.class);//http请求参数映射到bean
		if (StringUtils.isBlank(form.getName())){
			throw new ActionException("video_file_name_empty!");
		}
		Video bean = Video.INSTANCE.Get(form.getId());
		java.io.File videoFile = ctx.file("file");
		if (videoFile != null){
			if (!Video.isLegalFile(videoFile.getName())){
				throw new ActionException("file_illegal");
			}
			bean.setSize(HumanReadableFilesize.readableFilesize(videoFile.length()));//更新文件size
			StorageService.VIDEOFILES.save(videoFile,bean.getPath());//更新视频文件，将文件保存到相应的目录
			VideoService.update(bean, form.getName(),HumanReadableFilesize.readableFilesize(videoFile.length()),form.getIntroduce());
		}else{
			VideoService.update(bean, form.getName(),bean.getSize(),form.getIntroduce());
		}
		//bean.setName(form.getName());
		//bean.setIntroduce(form.getIntroduce());//更新视频简介
		//bean.updateAttrs(new String[] { "name", "size" , "introduce"}, new Object[] { bean.getName(), bean.getSize(), bean.getIntroduce()});
		//VideoService.update(bean, form.getName(),videoFile.length(),form.getIntroduce());
		ctx.output_json("msg", "");
	}
	
	protected Video safeVideo(RequestContext ctx) {
		int videoid = ctx.param("id", 0);
		if (videoid == 0) {
			throw new ActionException("Parameter docid is necessary");
		}
		Video video = Video.INSTANCE.Get(videoid);
		if (video == null) {
			throw new ActionException("The video[id:" + videoid + "] is not exists");
		}
		return video;
	}
	/**
	 * 上传视频图片
	 * @param req
	 * @param res
	 * @throws IOException 
	 */
	@Annotation.PostMethod
	@Annotation.UserRoleRequired
	@Annotation.JSONOutputEnabled
	public void upload_videopic(RequestContext ctx) throws IOException {
		if(!ctx.isUpload()){
			throw new ActionException("Need a file to upload");
		}
		File imgFile = ctx.image("img");
		if(imgFile == null) {
			throw new ActionException("Img file not accept");
		}
		if(imgFile.length() > MAX_IMAGE_SIZE ) {
			throw new ActionException("图像文件大小不能超过3M！");
		}
		//获取上传标本图片对象
		Video video = safeVideo(ctx);
		StorageService ss = StorageService.VIDEOPICS;
		String ext = "." + FilenameUtils.getExtension(imgFile.getName());
		String uri = video.imageOriginalUri(ext);//原始图片名称
		int[] sizes = Multimedia.saveImage(imgFile, ss.getBasePath() + uri);
		if(sizes != null){
			int[] scaledSizes = JavaImgHandler.getShrinkSize(sizes[0], sizes[1], 640);
			uri = video.imageShrinkUri(ext);//缩放后的图片名称
			Multimedia.saveImage(imgFile, ss.getBasePath() + uri, scaledSizes[0], scaledSizes[1]);
			video.updateAttr("video_pic", uri);//更新数据库video_pic字段
			ctx.output_json(
				new String[]{"img","width","height"}, 
				new Object[]{ss.getReadPath() + uri, scaledSizes[0], scaledSizes[1]}
			);
		}
	}
	
	/**
	 * 保存视频图片
	 * @param req
	 * @param res
	 * @throws IOException 
	 */
	@Annotation.PostMethod
	@Annotation.UserRoleRequired
	@Annotation.JSONOutputEnabled
	public void save_videopic(RequestContext ctx) throws IOException {
		int top = ctx.param("top", 0);
		int left = ctx.param("left", 0);
		int width = ctx.param("width", 0);
		int height = ctx.param("height", 0);
		
		//获取上传视频图片对象
		Video video = safeVideo(ctx);
		StorageService ss = StorageService.VIDEOPICS;
		String basePath = ss.getBasePath();
		
		String uri = video.getVideo_pic();//获得原始图片uri
		String ext = "." + FilenameUtils.getExtension(uri);
		String s_uri = video.smallImage(ext);
		//由原图片保存小图
		Multimedia.saveImage(new File(basePath + uri), basePath + s_uri, 
		  top, left, width, height, Video.IMAGE_WIDTH, Video.IMAGE_HEIGHT);
		//更新数据库中缩略图名称
		video.updateAttr("video_thumbpic", s_uri);//更新数据库photo字段
		//由原图片保存大图
		String b_uri = video.bigImage(ext);
		Multimedia.saveImage(new File(basePath + uri), basePath + b_uri, 
				  top, left, width, height, width, height);
		//更新数据库的大图名称
		video.updateAttr("video_pic", b_uri);//更新数据库photo字段
		ctx.output_json("msg", "图片更新成功");
	}
}
