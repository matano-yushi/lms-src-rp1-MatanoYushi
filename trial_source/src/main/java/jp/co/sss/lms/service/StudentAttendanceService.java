package jp.co.sss.lms.service;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.validation.BindingResult;

import jp.co.sss.lms.dto.AttendanceManagementDto;
import jp.co.sss.lms.dto.LoginUserDto;
import jp.co.sss.lms.entity.TStudentAttendance;
import jp.co.sss.lms.enums.AttendanceStatusEnum;
import jp.co.sss.lms.form.AttendanceForm;
import jp.co.sss.lms.form.DailyAttendanceForm;
import jp.co.sss.lms.mapper.TStudentAttendanceMapper;
import jp.co.sss.lms.util.AttendanceUtil;
import jp.co.sss.lms.util.Constants;
import jp.co.sss.lms.util.DateUtil;
import jp.co.sss.lms.util.LoginUserUtil;
import jp.co.sss.lms.util.MessageUtil;
import jp.co.sss.lms.util.TrainingTime;

/**
 * 勤怠情報（受講生入力）サービス
 * 
 * @author 東京ITスクール
 */
@Service
public class StudentAttendanceService {

	@Autowired
	private DateUtil dateUtil;
	@Autowired
	private AttendanceUtil attendanceUtil;
	@Autowired
	private MessageUtil messageUtil;
	@Autowired
	private LoginUserUtil loginUserUtil;
	@Autowired
	private LoginUserDto loginUserDto;
	@Autowired
	private TStudentAttendanceMapper tStudentAttendanceMapper;

	/**
	 * 勤怠一覧情報取得
	 * 
	 * @param courseId
	 * @param lmsUserId
	 * @return 勤怠管理画面用DTOリスト
	 */
	public List<AttendanceManagementDto> getAttendanceManagement(Integer courseId,
			Integer lmsUserId) {

		// 勤怠管理リストの取得
		List<AttendanceManagementDto> attendanceManagementDtoList = tStudentAttendanceMapper
				.getAttendanceManagement(courseId, lmsUserId, Constants.DB_FLG_FALSE);
		for (AttendanceManagementDto dto : attendanceManagementDtoList) {
			// 中抜け時間を設定
			if (dto.getBlankTime() != null) {
				TrainingTime blankTime = attendanceUtil.calcBlankTime(dto.getBlankTime());
				dto.setBlankTimeValue(String.valueOf(blankTime));
			}
			// 遅刻早退区分判定
			AttendanceStatusEnum statusEnum = AttendanceStatusEnum.getEnum(dto.getStatus());
			if (statusEnum != null) {
				dto.setStatusDispName(statusEnum.name);
			}
		}

		return attendanceManagementDtoList;
	}

	/**
	 * 出退勤更新前のチェック
	 * 
	 * @param attendanceType
	 * @return エラーメッセージ
	 */
	public String punchCheck(Short attendanceType) {
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 権限チェック
		if (!loginUserUtil.isStudent()) {
			return messageUtil.getMessage(Constants.VALID_KEY_AUTHORIZATION);
		}
		// 研修日チェック
		if (!attendanceUtil.isWorkDay(loginUserDto.getCourseId(), trainingDate)) {
			return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_NOTWORKDAY);
		}
		// 登録情報チェック
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate,
						Constants.DB_FLG_FALSE);
		switch (attendanceType) {
		case Constants.CODE_VAL_ATWORK:
			if (tStudentAttendance != null
					&& !tStudentAttendance.getTrainingStartTime().equals("")) {
				// 本日の勤怠情報は既に入力されています。直接編集してください。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHALREADYEXISTS);
			}
			break;
		case Constants.CODE_VAL_LEAVING:
			if (tStudentAttendance == null
					|| tStudentAttendance.getTrainingStartTime().equals("")) {
				// 出勤情報がないため退勤情報を入力出来ません。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHINEMPTY);
			}
			if (!tStudentAttendance.getTrainingEndTime().equals("")) {
				// 本日の勤怠情報は既に入力されています。直接編集してください。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHALREADYEXISTS);
			}
			TrainingTime trainingStartTime = new TrainingTime(
					tStudentAttendance.getTrainingStartTime());
			TrainingTime trainingEndTime = new TrainingTime();
			if (trainingStartTime.compareTo(trainingEndTime) > 0) {
				// 退勤時刻は出勤時刻より後でなければいけません。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_TRAININGTIMERANGE);
			}
			break;
		}
		return null;
	}

	/**
	 * 出勤ボタン処理
	 * 
	 * @return 完了メッセージ
	 */
	public String setPunchIn() {
		// 当日日付
		Date date = new Date();
		// 本日の研修日
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 現在の研修時刻
		TrainingTime trainingStartTime = new TrainingTime();
		// 遅刻早退ステータス
		AttendanceStatusEnum attendanceStatusEnum = attendanceUtil.getStatus(trainingStartTime,
				null);
		// 研修日の勤怠情報取得
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate,
						Constants.DB_FLG_FALSE);
		if (tStudentAttendance == null) {
			// 登録処理
			tStudentAttendance = new TStudentAttendance();
			tStudentAttendance.setLmsUserId(loginUserDto.getLmsUserId());
			tStudentAttendance.setTrainingDate(trainingDate);
			tStudentAttendance.setTrainingStartTime(trainingStartTime.toString());
			tStudentAttendance.setTrainingEndTime("");
			tStudentAttendance.setStatus(attendanceStatusEnum.code);
			tStudentAttendance.setNote("");
			tStudentAttendance.setAccountId(loginUserDto.getAccountId());
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			tStudentAttendance.setFirstCreateUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setFirstCreateDate(date);
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			tStudentAttendance.setBlankTime(null);
			tStudentAttendanceMapper.insert(tStudentAttendance);
		} else {
			// 更新処理
			tStudentAttendance.setTrainingStartTime(trainingStartTime.toString());
			tStudentAttendance.setStatus(attendanceStatusEnum.code);
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			tStudentAttendanceMapper.update(tStudentAttendance);
		}
		// 完了メッセージ
		return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
	}

	/**
	 * 退勤ボタン処理
	 * 
	 * @return 完了メッセージ
	 */
	public String setPunchOut() {
		// 当日日付
		Date date = new Date();
		// 本日の研修日
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 研修日の勤怠情報取得
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate,
						Constants.DB_FLG_FALSE);
		// 出退勤時刻
		TrainingTime trainingStartTime = new TrainingTime(
				tStudentAttendance.getTrainingStartTime());
		TrainingTime trainingEndTime = new TrainingTime();
		// 遅刻早退ステータス
		AttendanceStatusEnum attendanceStatusEnum = attendanceUtil.getStatus(trainingStartTime,
				trainingEndTime);
		// 更新処理
		tStudentAttendance.setTrainingEndTime(trainingEndTime.toString());
		tStudentAttendance.setStatus(attendanceStatusEnum.code);
		tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
		tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
		tStudentAttendance.setLastModifiedDate(date);
		tStudentAttendanceMapper.update(tStudentAttendance);
		// 完了メッセージ
		return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
	}

	/**
	 * 勤怠フォームへ設定
	 * @author 俣野宥士　-Task26
	 * @param attendanceManagementDtoList
	 * @return 勤怠編集フォーム
	 */
	public AttendanceForm setAttendanceForm(
			List<AttendanceManagementDto> attendanceManagementDtoList) {

		AttendanceForm attendanceForm = new AttendanceForm();
		attendanceForm.setAttendanceList(new ArrayList<DailyAttendanceForm>());
		attendanceForm.setLmsUserId(loginUserDto.getLmsUserId());
		attendanceForm.setUserName(loginUserDto.getUserName());
		attendanceForm.setLeaveFlg(loginUserDto.getLeaveFlg());
		attendanceForm.setBlankTimes(attendanceUtil.setBlankTime());

		//時と分のダイアログ処理
		LinkedHashMap<Integer, String> hourMap = new LinkedHashMap<>();
		hourMap.put(null, "");
		for (int i = 0; i < 24; i++) {
			hourMap.put(i, String.format("%02d", i));
		}
		attendanceForm.setHourMap(hourMap);
		LinkedHashMap<Integer, String> minutesMap = new LinkedHashMap<>();
		minutesMap.put(null, "");
		for (int j = 0; j < 60; j++) {
			minutesMap.put(j, String.format("%02d", j));
		}
		attendanceForm.setMinutesMap(minutesMap);

		// 途中退校している場合のみ設定
		if (loginUserDto.getLeaveDate() != null) {
			attendanceForm.setLeaveDate(dateUtil.dateToString(loginUserDto.getLeaveDate(), "yyyy-MM-dd"));
			attendanceForm.setDispLeaveDate(dateUtil.dateToString(loginUserDto.getLeaveDate(), "yyyy年M月d日"));
		}

		// 勤怠管理リストの件数分、日次の勤怠フォームに移し替え
		for (AttendanceManagementDto attendanceManagementDto : attendanceManagementDtoList) {
			DailyAttendanceForm dailyAttendanceForm = new DailyAttendanceForm();
			dailyAttendanceForm
					.setStudentAttendanceId(attendanceManagementDto.getStudentAttendanceId());
			dailyAttendanceForm
					.setTrainingDate(dateUtil.toString(attendanceManagementDto.getTrainingDate()));
			dailyAttendanceForm
					.setTrainingStartTime(attendanceManagementDto.getTrainingStartTime());
			dailyAttendanceForm.setTrainingEndTime(attendanceManagementDto.getTrainingEndTime());
			if (attendanceManagementDto.getBlankTime() != null) {
				dailyAttendanceForm.setBlankTime(attendanceManagementDto.getBlankTime());
				dailyAttendanceForm.setBlankTimeValue(String.valueOf(
						attendanceUtil.calcBlankTime(attendanceManagementDto.getBlankTime())));
			}
			dailyAttendanceForm.setStatus(String.valueOf(attendanceManagementDto.getStatus()));
			dailyAttendanceForm.setNote(attendanceManagementDto.getNote());
			dailyAttendanceForm.setSectionName(attendanceManagementDto.getSectionName());
			dailyAttendanceForm.setIsToday(attendanceManagementDto.getIsToday());
			dailyAttendanceForm.setDispTrainingDate(dateUtil
					.dateToString(attendanceManagementDto.getTrainingDate(), "yyyy年M月d日(E)"));
			dailyAttendanceForm.setStatusDispName(attendanceManagementDto.getStatusDispName());

			//出勤（時・分）、退勤（時・分）受け取り分解処理
			String timeString = attendanceManagementDto.getTrainingStartTime();
			if (timeString != null && !timeString.isEmpty()) {
				int startHour = Integer.parseInt(timeString.substring(0, 2));
				int startMinutes = Integer.parseInt(timeString.substring(3, 5));
				dailyAttendanceForm.setStartHour(startHour);
				dailyAttendanceForm.setStartMinutes(startMinutes);
			}
			String endString = attendanceManagementDto.getTrainingEndTime();
			if (endString != null && !endString.isEmpty()) {
				int endHour = Integer.parseInt(endString.substring(0, 2));
				int endMinutes = Integer.parseInt(endString.substring(3, 5));
				dailyAttendanceForm.setEndHour(endHour);
				dailyAttendanceForm.setEndMinutes(endMinutes);
			}

			attendanceForm.getAttendanceList().add(dailyAttendanceForm);
		}

		return attendanceForm;

	}

	/**
	 * 勤怠登録・更新処理
	 * 
	 * @param attendanceForm
	 * @return 完了メッセージ
	 * @throws ParseException
	 */
	public String update(AttendanceForm attendanceForm) throws ParseException {

		Integer lmsUserId = loginUserUtil.isStudent() ? loginUserDto.getLmsUserId()
				: attendanceForm.getLmsUserId();

		// 現在の勤怠情報（受講生入力）リストを取得
		List<TStudentAttendance> tStudentAttendanceList = tStudentAttendanceMapper
				.findByLmsUserId(lmsUserId, Constants.DB_FLG_FALSE);

		// 入力された情報を更新用のエンティティに移し替え
		Date date = new Date();
		for (DailyAttendanceForm dailyAttendanceForm : attendanceForm.getAttendanceList()) {

			// 更新用エンティティ作成
			TStudentAttendance tStudentAttendance = new TStudentAttendance();
			// 日次勤怠フォームから更新用のエンティティにコピー
			BeanUtils.copyProperties(dailyAttendanceForm, tStudentAttendance);
			// 研修日付
			tStudentAttendance
					.setTrainingDate(dateUtil.parse(dailyAttendanceForm.getTrainingDate()));
			// 現在の勤怠情報リストのうち、研修日が同じものを更新用エンティティで上書き
			for (TStudentAttendance entity : tStudentAttendanceList) {
				if (entity.getTrainingDate().equals(tStudentAttendance.getTrainingDate())) {
					tStudentAttendance = entity;
					break;
				}
			}
			tStudentAttendance.setLmsUserId(lmsUserId);
			tStudentAttendance.setAccountId(loginUserDto.getAccountId());
			// 出勤時刻整形
			TrainingTime trainingStartTime = null;
			trainingStartTime = new TrainingTime(dailyAttendanceForm.getTrainingStartTime());
			tStudentAttendance.setTrainingStartTime(trainingStartTime.getFormattedString());
			// 退勤時刻整形
			TrainingTime trainingEndTime = null;
			trainingEndTime = new TrainingTime(dailyAttendanceForm.getTrainingEndTime());
			tStudentAttendance.setTrainingEndTime(trainingEndTime.getFormattedString());
			// 中抜け時間
			tStudentAttendance.setBlankTime(dailyAttendanceForm.getBlankTime());
			// 遅刻早退ステータス
			if ((trainingStartTime != null || trainingEndTime != null)
					&& !dailyAttendanceForm.getStatusDispName().equals("欠席")) {
				AttendanceStatusEnum attendanceStatusEnum = attendanceUtil
						.getStatus(trainingStartTime, trainingEndTime);
				tStudentAttendance.setStatus(attendanceStatusEnum.code);
			}
			// 備考
			tStudentAttendance.setNote(dailyAttendanceForm.getNote());
			// 更新者と更新日時
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			// 削除フラグ
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			// 登録用Listへ追加
			tStudentAttendanceList.add(tStudentAttendance);
		}
		// 登録・更新処理
		for (TStudentAttendance tStudentAttendance : tStudentAttendanceList) {
			if (tStudentAttendance.getStudentAttendanceId() == null) {
				tStudentAttendance.setFirstCreateUser(loginUserDto.getLmsUserId());
				tStudentAttendance.setFirstCreateDate(date);
				tStudentAttendanceMapper.insert(tStudentAttendance);
			} else {
				tStudentAttendanceMapper.update(tStudentAttendance);
			}
		}
		// 完了メッセージ
		return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
	}
	//過去日の未入力チェック
	//今日より前の過去日に、未入力の勤怠があるかどうかを判定する

	/**
	 * 勤怠管理(過去日が未入力の場合の処理）
	 * @author 俣野宥士-Task.25
	 * @throws ParseException
	 * @return 未打刻が存在する場合は true、それ以外は false
	 *
	 */

	//sdf.parse()の日付変換処理があるから、例外処理記載
	public Boolean notEnterCheck() throws ParseException {
		//現在の日付をUtilからとってきてる
		Date trainingDate = attendanceUtil.getTrainingDate();
		Integer lmsUserId = loginUserDto.getLmsUserId();
		short deleteFlg = 0;
		//サービス→Mapperに（）の中を渡してる、上の条件をMapperに渡してる
		Integer notEnterCount = tStudentAttendanceMapper.notEnterCount(lmsUserId, deleteFlg, trainingDate);

		if (notEnterCount > 0) {
			return true;
		} else {
			return false;
		}

	}

	/**
	 * 入力フォームからの出勤（時・分）退勤（時・分）データ結合
	 * @author 俣野宥士　-Task26
	 * 
	 */

	public void formatConvaersion(AttendanceForm attendanceForm) {
		if (attendanceForm == null || attendanceForm.getAttendanceList() == null) {
			return;
		}
		for (DailyAttendanceForm dailyAttendanceForm : attendanceForm.getAttendanceList()) {
			Integer startHour = dailyAttendanceForm.getStartHour();
			Integer startMinutes = dailyAttendanceForm.getStartMinutes();
			if (startHour != null && startMinutes != null) {
				String trainingStartTime = String.format("%02d:%02d", startHour, startMinutes);
				dailyAttendanceForm.setTrainingStartTime(trainingStartTime);
			}
			Integer endHour = dailyAttendanceForm.getEndHour();
			Integer endminutes = dailyAttendanceForm.getEndMinutes();
			if (endHour != null && endminutes != null) {
				String trainingEndTime = String.format("%02d:%02d", endHour, endminutes);
				dailyAttendanceForm.setTrainingEndTime(trainingEndTime);

			}

		}

	}

	/**
	 * 勤怠入力チェック
	 * 俣野宥士-Task27
	 * 
	 */
	public void updateInputCheck(AttendanceForm attendanceForm, BindingResult result) {
		//getAttendanceListは１日分だから、DailyAttendanceForm型になる
		List<DailyAttendanceForm> dailyAttendanceForm = attendanceForm.getAttendanceList();
		//1か月繰り返してる
		for (int i = 0; i < dailyAttendanceForm.size(); i++) {
			//１日文のデータを変数に入れてる
			DailyAttendanceForm dailyForm = dailyAttendanceForm.get(i);
			//lengh()で文字数の長さ見てる
			if (dailyForm.getNote() != null && dailyForm.getNote().length() > 100) {
				//エラーを出す画面の場所指定
				String errorNote = "attendanceList[" + i + "].note";
				//result.rejectValue(...）・・・エラーの場合の処理記載
				//errorNote・・・エラーを出す場所
				//null・・・必須、テンプレート見つからなかった場合の処理
				result.rejectValue(errorNote, "maxlength", new Object[] { "備考", 100 }, null);

			}
			//退勤時間（時・分）片方未入力チェック
			Integer startHour = dailyForm.getStartHour();
			Integer startMinutes = dailyForm.getStartMinutes();
			if ((startHour != null && startMinutes == null) || (startHour == null && startMinutes != null)) {
				String errorStartHourTime = "attendanceList[" + i + "].startHour";
				String errorStartMinutesTime = "attendanceList[" + i + "].startMinutes";
				result.rejectValue(errorStartHourTime, "input.invalid", new Object[] { "出勤時間", }, null);
				result.rejectValue(errorStartMinutesTime, "input.invalid", new Object[] { "出勤時間", }, null);

			}
			//出勤時間（時・分）片方未入力チェック
			Integer endHour = dailyForm.getEndHour();
			Integer endMinutes = dailyForm.getEndMinutes();
			if ((endHour != null && endMinutes == null) || (endHour == null && endMinutes != null)) {
				String errorEndHourTime = "attendanceList[" + i + "].endHour";
				String errorEndMinutesTime = "attendanceList[" + i + "].endMinutes";
				result.rejectValue(errorEndHourTime, "input.invalid", new Object[] { "退勤時間", }, null);
				result.rejectValue(errorEndMinutesTime, "input.invalid", new Object[] { "退勤時間", }, null);

			}
			//出勤時間・退勤時間　ありなしパターン
			String trainingStartTime = dailyForm.getTrainingStartTime();
			String trainingEndTime = dailyForm.getTrainingEndTime();
			if (trainingStartTime == null && trainingEndTime != null) {
				String errorStartAndEnd = "attendanceList[" + i + "].trainingStartTime";
				result.rejectValue(errorStartAndEnd, "attendance.punchInEmpty");

			}
			//出勤時間＞退勤時間チェック
			if (trainingStartTime != null && trainingEndTime != null) {
				//:があるので、compareToで比較
				if (trainingStartTime.compareTo(trainingEndTime) > 0) {
					String errortrainingStartTimeBig = "attendanceList[" + i + "].trainingEndTime";
					result.rejectValue(errortrainingStartTimeBig, "attendance.trainingTimeRange",
							new Object[] { trainingEndTime, trainingStartTime }, null);

				}
			}
			//中抜け時間>勤務時間
			Integer blankTime = dailyForm.getBlankTime();
			if (startHour != null && startMinutes != null && endHour != null && endMinutes != null
					&& blankTime != null) {
				int startWorkMinutesTime = dailyForm.getStartHour() * 60 + dailyForm.getStartMinutes();
				int endWorkMinutesTime = dailyForm.getEndHour() * 60 + dailyForm.getEndMinutes();
				int totalWorkTime = endWorkMinutesTime - startWorkMinutesTime;
				if (blankTime > totalWorkTime) {
					String errorBlankTime = "attendanceList[" + i + "].blankTime";
					result.rejectValue(errorBlankTime, "attendanceblankTimeError");
				}

			}

		}

	}
}