package com.group.pbox.pvbs.controller.termdeposit;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import com.group.pbox.pvbs.acct.IAcctCreationService;
import com.group.pbox.pvbs.clientmodel.acct.AcctReqModel;
import com.group.pbox.pvbs.clientmodel.acct.AcctRespModel;

import com.group.pbox.pvbs.clientmodel.sysconf.SysConfReqModel;
import com.group.pbox.pvbs.clientmodel.sysconf.SysConfRespModel;
import com.group.pbox.pvbs.clientmodel.termdeposit.TermDepositReqModel;
import com.group.pbox.pvbs.clientmodel.termdeposit.TermDepositRespModel;
import com.group.pbox.pvbs.clientmodel.transaction.TransactionReqModel;
import com.group.pbox.pvbs.clientmodel.transaction.TransactionRespModel;
import com.group.pbox.pvbs.sysconf.ISysConfService;
import com.group.pbox.pvbs.termdeposit.ITermDepositService;
import com.group.pbox.pvbs.transaction.IAccountBalanceService;
import com.group.pbox.pvbs.clientmodel.termdeposit.TermDepositRateRespModel;
import com.group.pbox.pvbs.clientmodel.user.UserReqModel;
import com.group.pbox.pvbs.clientmodel.user.UserRespModel;
import com.group.pbox.pvbs.termdeposit.ITermDepositRateService;
import com.group.pbox.pvbs.user.IUserService;

import com.group.pbox.pvbs.util.ErrorCode;
import com.group.pbox.pvbs.util.OperationCode;

@Controller
@RequestMapping("/termDeposit")
public class TermDepositController {
	private static final Logger businessLogger = Logger.getLogger(TermDepositController.class);
	private static final Logger sysLogger = Logger.getLogger("customer");

	@Resource
	IAcctCreationService acctCreationService;
	@Resource
	ITermDepositService termDepositService;
    @Resource
    IAccountBalanceService accountBalanceService;
	@Resource
	ISysConfService sysConfService;

	@Resource
	ITermDepositRateService termDepositRateService;

	@Resource
	IUserService userSrvice;

	@RequestMapping(value = "/termDepositDepatcher", method = RequestMethod.POST, consumes = "application/json")
	@ResponseBody
	public Object termDeposit(final HttpServletRequest request, final HttpServletResponse response,
			@RequestBody TermDepositReqModel termDepositReqModel) {

		TermDepositRespModel termDepositRespModel = new TermDepositRespModel();
		List<String> errorList = new ArrayList<String>();

		try {
			switch (termDepositReqModel.getOperationCode()) {
			case OperationCode.TERM_CREATE:

				termDepositRespModel = createTermDeposit(termDepositReqModel, request);
				break;
			case OperationCode.TERM_ENQUIRY:

				termDepositRespModel = enquiryTermDeposit(termDepositReqModel);
				break;
			case OperationCode.TERM_DRAWDOWN:

				termDepositRespModel = drawDown(termDepositReqModel);
				break;
			case OperationCode.TERM_RENEWAL:

				termDepositRespModel = reNewal(termDepositReqModel);
				break;

			}

		} catch (Exception e) {
			sysLogger.error("", e);
			errorList.add(ErrorCode.SYSTEM_OPERATION_ERROR);
			termDepositRespModel.setResult(ErrorCode.RESPONSE_ERROR);
			termDepositRespModel.setErrorCode(errorList);
		}
		return termDepositRespModel;
	}

	@RequestMapping(value = "/termDepositRate", method = RequestMethod.POST, consumes = "application/json")
	@ResponseBody
	public Object termDepositRate(final HttpServletRequest request, final HttpServletResponse response,
			@RequestBody TermDepositReqModel termDepositReqModel) {

		TermDepositRateRespModel termDepositRateRespModel = new TermDepositRateRespModel();
		List<String> errorList = new ArrayList<String>();

		try {
			switch (termDepositReqModel.getOperationCode()) {
			case OperationCode.FETCH_TD_RATE:
					termDepositRateRespModel = termDepositRateService.inquiryAllTermDepositRate();
			}

		}catch (Exception e) {
			sysLogger.error("", e);
			errorList.add(ErrorCode.SYSTEM_OPERATION_ERROR);
			termDepositRateRespModel.setResult(ErrorCode.RESPONSE_ERROR);
			termDepositRateRespModel.setErrorCode(errorList);
		}

		return termDepositRateRespModel;
	}

	private TermDepositRespModel reNewal(TermDepositReqModel termDepositReqModel) {
		// TODO Auto-generated method stub
		return null;
	}

	private TermDepositRespModel drawDown(TermDepositReqModel termDepositReqModel) throws Exception {
		TermDepositRespModel termDepositResp = new TermDepositRespModel();
		TransactionReqModel transactionReqModel = new TransactionReqModel();
		TransactionRespModel transactionRespModel = new TransactionRespModel();
		AcctReqModel acctRequest = new AcctReqModel();
		acctRequest.setRealAccountNumber(termDepositReqModel.getAccountNumber());
		AcctRespModel acctValid = acctCreationService.accountValidByRealNum(acctRequest);
		if ( acctValid.getResult().equals(ErrorCode.RESPONSE_ERROR)) {
			termDepositResp.setResult(ErrorCode.RESPONSE_ERROR);
			termDepositResp.getErrorCode().add(ErrorCode.RECORD_NOT_FOUND);
			return termDepositResp;
		}
		
		termDepositResp = termDepositService.drawDown(termDepositReqModel);
		//enquery balance
		SysConfReqModel sysConfReqModel = new SysConfReqModel();
		sysConfReqModel.setItem("Primary_Ccy_Code");
		SysConfRespModel sysConfRespModel = new SysConfRespModel();
		sysConfRespModel = sysConfService.getAllSysConfByParam(sysConfReqModel);
		String primaryCode = sysConfRespModel.getListData().get(0).getValue();
		transactionRespModel = accountBalanceService.enquireAccountBalance(termDepositReqModel.getAccountNumber());
		double acctBalance = 0;
		for (int i = 0; i < transactionRespModel.getListData().size(); i++) {
			if (primaryCode.equals(transactionRespModel.getListData().get(i).getCurrencyCode()))
			{
				acctBalance = transactionRespModel.getListData().get(i).getBalance();
			}
		}
		
		//update balance
		double maturityAmount = termDepositReqModel.getMaturityAmount();
		transactionReqModel.setAccountNumber(termDepositReqModel.getAccountNumber());
		transactionReqModel.setAmount(acctBalance+maturityAmount);
		transactionReqModel.setCurrency(primaryCode);
		transactionRespModel = accountBalanceService.deposit(transactionReqModel);
		
		if (ErrorCode.RESPONSE_ERROR.equals(transactionRespModel.getResult()))
		{
			termDepositResp.setResult(ErrorCode.RESPONSE_ERROR);
			termDepositResp.getErrorCode().add(ErrorCode.UPDATE_BALANCE_FAIL);
			return termDepositResp;
		}
		
		//edit status to 'D'
		termDepositResp = termDepositService.updateStatus(termDepositReqModel);
		
		return termDepositResp;
	}

	private TermDepositRespModel enquiryTermDeposit(TermDepositReqModel termDepositReqModel) {
		// TODO Auto-generated method stub
		return null;
	}

	private TermDepositRespModel createTermDeposit(TermDepositReqModel termDepositReqModel, HttpServletRequest request) throws Exception {
		TermDepositRespModel termDepositRespModel = new TermDepositRespModel();

		//validate the transaction account number
		termDepositRespModel = checkTransactionAcctValid(termDepositReqModel);
		if (StringUtils.equalsIgnoreCase(termDepositRespModel.getResult(), ErrorCode.RESPONSE_ERROR)) {
			return termDepositRespModel;
		}

		//validate the debit account number
		termDepositRespModel = checkDebitAcctValid(termDepositReqModel);
		if (StringUtils.equalsIgnoreCase(termDepositRespModel.getResult(), ErrorCode.RESPONSE_ERROR)) {
			return termDepositRespModel;
		}

		//validate the exceedLimit from User table
		termDepositRespModel = checkUserIdAndGetLimit(termDepositReqModel, request);
		if (StringUtils.equalsIgnoreCase(termDepositRespModel.getResult(), ErrorCode.RESPONSE_ERROR)) {
			return termDepositRespModel;
		}

		//Caculate the TD Maturity Interest
		/*TODO*/termDepositReqModel.setMaturityInterset(
			termDepositReqModel.getDepositAmount() * termDepositReqModel.getTermInterestRate());

		termDepositRespModel = termDepositService.creatTermDeposit(termDepositReqModel);

		return termDepositRespModel;
	}

	private TermDepositRespModel checkTransactionAcctValid(TermDepositReqModel termDepositReqModel) throws Exception {
		TermDepositRespModel termDepositRespModel = new TermDepositRespModel();

		AcctReqModel acctReqModel = new AcctReqModel();
		acctReqModel.setRealAccountNumber(termDepositReqModel.getTransAccountNum());
		AcctRespModel acctRespModel = new AcctRespModel();

		acctRespModel = acctCreationService.accountValidByRealNum(acctReqModel);

		if (StringUtils.equalsIgnoreCase(acctRespModel.getResult(), ErrorCode.RESPONSE_ERROR)) {
			termDepositRespModel.setResult(ErrorCode.RESPONSE_ERROR);
			termDepositRespModel.setErrorCode(acctRespModel.getErrorCode());
		}

		return termDepositRespModel;
	}

	private TermDepositRespModel checkDebitAcctValid(TermDepositReqModel termDepositReqModel) throws Exception {
		TermDepositRespModel termDepositRespModel = new TermDepositRespModel();

		AcctReqModel acctReqModel = new AcctReqModel();
		acctReqModel.setRealAccountNumber(termDepositReqModel.getAccountNumber());
		AcctRespModel acctRespModel = new AcctRespModel();

		acctRespModel = acctCreationService.accountValidByRealNum(acctReqModel);

		if (StringUtils.equalsIgnoreCase(acctRespModel.getResult(), ErrorCode.RESPONSE_ERROR)) {
			termDepositRespModel.setResult(ErrorCode.RESPONSE_ERROR);
			termDepositRespModel.setErrorCode(acctRespModel.getErrorCode());
		} else {
			//validate the primary ccy balance
			/*TODO*/
		}

		return termDepositRespModel;
	}
	
	private TermDepositRespModel checkUserIdAndGetLimit(TermDepositReqModel termDepositReqModel,HttpServletRequest request) {
		TermDepositRespModel termDepositRespModel = new TermDepositRespModel();
		UserReqModel userReqModel = new UserReqModel();

		/*read userId from session*/
		String userId = (String) request.getSession().getAttribute("userId");
		userReqModel.setUserId(userId);

		UserRespModel userRespModel = userSrvice.fetchUserByUserId(userReqModel);

		if (termDepositReqModel.getDepositAmount() > userRespModel.getListData().get(0).getTermDepositeLimit()) {
			termDepositRespModel.setResult(ErrorCode.RESPONSE_ERROR);
			termDepositRespModel.getErrorCode().add(ErrorCode.EXCEED_LIMIT);
		}

		return termDepositRespModel;
	}
	
}
