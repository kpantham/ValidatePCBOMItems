package com.gilead.agile.px.validatepcbomitems;

import com.agile.api.APIException;
import com.agile.api.ChangeConstants;
import com.agile.api.CommonConstants;
import com.agile.api.IAgileClass;
import com.agile.api.IAgileSession;
import com.agile.api.ICell;
import com.agile.api.IChange;
import com.agile.api.IDataObject;
import com.agile.api.IItem;
import com.agile.api.INode;
import com.agile.api.IRow;
import com.agile.api.ISignoffReviewer;
import com.agile.api.IStatus;
import com.agile.api.ITable;
import com.agile.api.IUser;
import com.agile.api.ItemConstants;
import com.agile.api.StatusConstants;
import com.agile.api.WorkflowConstants;
import com.agile.px.ActionResult;
import com.agile.px.EventActionResult;
import com.agile.px.IEventAction;
import com.agile.px.IEventInfo;
import com.agile.px.IObjectEventInfo;
import com.gilead.agile.px.common.PXInit;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class ValidatePCBOMItemsEvent
  implements IEventAction
{
  public static Logger logger = null;
  public static Properties properties = null;
  
  static {
    String classname = MethodHandles.lookup().lookupClass().getName();
    String pxname = "ValidatePCBOMItems";
    String pxnumber = "0031";
    String pxid = "PX-" + pxnumber + "-" + pxname;
    String pxrev = "$Revision: 2963 $";
    try {
      properties = new Properties();
      PXInit.initLogger(pxname + "_log4j2.xml");
      logger = LogManager.getLogger(classname);
      logger.debug("init succeeded " + pxid + " " + pxrev);
      PXInit.initProperties(properties, pxname+".properties");
      logger.debug("properties loaded from " + properties.getProperty("pxpropfile"));
    } catch (Exception e) {
      logger = LogManager.getLogger(classname);
      logger.debug("init failed " + pxid + " " + pxrev);
    }
  }
  
  static ArrayList<String> erroMsgRemoved = new ArrayList();
  static ArrayList<String> bomErrors = new ArrayList();
  static ArrayList<String> ErrorsMandatoryAtt = new ArrayList();
  Properties prop = new Properties();
  public static String errorMsgR = "";
  public static String bomMessage = "";
  public static String changeNumberActual = "";
  public static String message = "";
  public static String messageRevision = "";
  static ArrayList<String> ErrorsRevision = new ArrayList();
  
  public ValidatePCBOMItemsEvent() {}
  
  public EventActionResult doAction(IAgileSession agileSession, INode node, IEventInfo request) { try { IObjectEventInfo objectEventInfo = (IObjectEventInfo)request;
      IDataObject dataObj = objectEventInfo.getDataObject();
      
      String listString = "";
      String bomlistString = "";
      String bomlistStringLC = "";
      String listStringRevision = "";
      

      logger.info(" new logger initialized");
      logger.debug(
        "Check for Mandatory attributes, Pending changes and Revision check from BOMs attached " + dataObj);
      
      IChange change = (IChange)dataObj;
      changeNumberActual = change.getValue(ChangeConstants.ATT_COVER_PAGE_NUMBER).toString();
      String currentStatus = change.getStatus().toString();
      String RAWF = properties.getProperty("ReviewApprovalWF").toString();
      String workflowCli = change.getValue(ChangeConstants.ATT_COVER_PAGE_WORKFLOW).toString();
      if (currentStatus.equals("Initiate")) {
        logger.info("Check for Mandatory attributes started at Initiate");
        checkMandatoryAttributes(change);
        if (workflowCli.equals(RAWF)) {
          checkMfrDiagram(change);
        }
      }
      logger.info("default sttaus type " + change.getDefaultNextStatus().getName());
      if (change.getDefaultNextStatus().getStatusType().equals(StatusConstants.TYPE_RELEASED)) {
        logger.debug("Check for Revision sequencing started at pre-Release ");
        checkRevision(change, agileSession);
      }
      ITable tableAI = change.getTable(ChangeConstants.TABLE_AFFECTEDITEMS);
      Iterator itAI = tableAI.iterator();
      String affItemTyp;
      IItem redlineItem1; while (itAI.hasNext()) {
        logger.debug("In AI table");
        IRow row = (IRow)itAI.next();
        IItem affItem = (IItem)row.getReferent();
        String affItemNumb = row.getValue(ChangeConstants.ATT_AFFECTED_ITEMS_ITEM_NUMBER).toString();
        affItemTyp = row.getValue(ChangeConstants.ATT_AFFECTED_ITEMS_ITEM_TYPE).toString();
        ITable tableRedlineBOM = affItem.getTable(ItemConstants.TABLE_REDLINEBOM);
        Iterator iteratorRedlineBOM = tableRedlineBOM.iterator();
        if (row.isFlagSet(ChangeConstants.FLAG_AI_ROW_HAS_REDLINE_BOM)) {
          logger.debug(affItemNumb + "is Redlined");
          while (iteratorRedlineBOM.hasNext()) {
            IRow rowRedlineBOM = (IRow)iteratorRedlineBOM.next();
            String redlineItem = rowRedlineBOM.getReferent().toString();
            if (rowRedlineBOM.isFlagSet(ItemConstants.FLAG_IS_REDLINE_ADDED)) {
              logger.debug(redlineItem + "is the newly added item to BOM");
              redlineItem1 = (IItem)agileSession.getObject(2, redlineItem);
              checkPD(redlineItem1, affItemNumb, affItemTyp);
            }
            if (rowRedlineBOM.isFlagSet(ItemConstants.FLAG_IS_REDLINE_REMOVED)) {
              logger.debug(redlineItem + "is the item which is going to be removed from BOM");
              errorMsgR = affItemNumb;
              erroMsgRemoved.add(errorMsgR);
            }
          }
        }
        String affItemN = row.getReferent().toString();
        String itemType = row.getValue(ChangeConstants.ATT_AFFECTED_ITEMS_ITEM_TYPE).toString();
        printBOM(affItem, affItemN, itemType);
      }
      
      ArrayList<String> errorsUnreleased = new ArrayList(bomErrors);
      errorsUnreleased.removeAll(erroMsgRemoved);
      Set<String> hs = new HashSet();
      hs.addAll(errorsUnreleased);
      errorsUnreleased.clear();
      errorsUnreleased.addAll(hs);
      
      for (String s : errorsUnreleased) {
        bomlistString = bomlistString + s + ",";
      }
      for (String sE : ErrorsMandatoryAtt) {
        listString = listString + sE + ". \n";
      }
      for (String sR : ErrorsRevision)
        listStringRevision = listStringRevision + sR + ". \n";
      EventActionResult localEventActionResult;
      if (ErrorsMandatoryAtt.isEmpty()) {
        logger.debug("All Mandatory attributes are filled");
        if (errorsUnreleased.isEmpty()) {
          logger.debug("There are no unreleased items or unappropriate LC phase under attached BOMs");
          if (ErrorsRevision.isEmpty()) {
            logger.debug("Previous revisions of the affected items are released");
          } else {
            return new EventActionResult(request, new ActionResult(-1, new Exception(listStringRevision)));
          }
        } else {
          logger.debug("inside else=================");
          



          String str = "";
          int niet = -1;
          IStatus status = change.getStatus();
          StatusConstants type = status.getStatusType();
          logger.debug("original niet value" + niet + "change status " + status);
          Object localObject1; if (type.equals(StatusConstants.TYPE_PENDING))
          {
            logger.debug("inside sttaus is pending niet value " + niet);
            str = "The Change (" + change.toString() + 
              ") is only in the \"Pending\" state.  It will advance and at that time check again for failures.";
            
            str = "\r\n\r\nNOTE:  " + str;
            niet = 0;
          } else {
            logger.debug("inside else status not pending niet value " + niet);
            str = "<big>\r\n\r\nIf you wish to override the warning:\r\n    1) Close this window.</big>                                                  <i>(select Cancel button in the lower right corner)</i><big>\r\n    2) Add \"Override, Warning\" to the Observers list.</big>    <i>(on the Workflow tab, select Add Reviewers button)</i><big>\r\n    3) Resubmitted the Change.</big>                                       <i>(select Next Status button)</i>";
            

            try
            {
              ISignoffReviewer[] sorList = change.getReviewers(status, WorkflowConstants.USER_OBSERVER);
              for(ISignoffReviewer sor : sorList)
              {
                IUser user = (IUser)sor.getReviewer();
                if (user.getName().equalsIgnoreCase("override_gplm_bom_child"))
                {
                  logger.debug("override user found=================niet value" + niet);
                  str = "This Change (" + change.toString() + 
                    ") is authorized to override the prior unapproved status or invalid lifecycles of the child parts!";
                  
                  str = "\r\n\r\nNOTE:  " + str;
                  niet = 0;
                  break;
                }
              }
            } catch (Exception ex) {
              str = "\r\n\r\nI Broke in Error!    Msg: " + ex.getMessage();
            }
          }
          logger.debug("Errors revision =" + ErrorsRevision.toString() + "niet value");
          
          if (ErrorsRevision.isEmpty())
          {
            logger.debug("inside if ErrorsRevision.isEmpty()");
            str = bomlistString + 
              " contain child items in unapproved status or invalid lifecycles. Please check in the BOM Tab." + 
              str;
          }
          else {
            logger.debug("inside if !ErrorsRevision.isEmpty(");
            StringBuilder errMsg = new StringBuilder();
            
            for (String temp : ErrorsRevision) {
              if (temp.contains("QA")) {
                errMsg = errMsg.append(temp);
              }
            }
            


            str = 
            
              bomlistString + " contain child items in unapproved status or invalid lifecycles. Please check in the BOM Tab." + str + "\n\n" + errMsg;
            logger.debug("str -=================" + str);
          }
          if (niet == 0)
          {
            logger.debug("inside if niet is 0");
            
            if (!ErrorsRevision.isEmpty()) {
              logger.debug("inside if !ErrorsRevision.isEmpty(");
              StringBuilder errMsg = new StringBuilder();
              StringBuilder errMsgRev = new StringBuilder();
              for (localObject1 = ErrorsRevision.iterator(); ((Iterator)localObject1).hasNext();) { String temp = (String)((Iterator)localObject1).next();
                if (temp.contains("QA")) {
                  errMsg = errMsg.append(temp);
                } else if (temp.contains("revision")) {
                  errMsgRev = errMsgRev.append(temp);
                  errMsgRev = errMsgRev.append("\n");
                }
              }
              



              if (errMsg.length() > 0)
              {
                logger.debug("errormessage -=================" + errMsg);
                return new EventActionResult(request, new ActionResult(-1, new Exception(errMsg.toString())));
              }
              if (errMsgRev.length() > 0)
              {
                logger.debug("errormessagerev -=====================" + errMsgRev);
                return new EventActionResult(request, new ActionResult(-1, new Exception(errMsgRev.toString())));
              }
              
              return new EventActionResult(request, new ActionResult(0, str));
            }
            

            logger.debug("inside if error revison is empty");
            return new EventActionResult(request, new ActionResult(0, "No errors"));
          }
          


          logger.debug("print str " + str);
          return new EventActionResult(request, new ActionResult(-1, new Exception(str)));
        }
        

      }
      else
      {

        return new EventActionResult(request, new ActionResult(-1, new Exception(listString)));
      }
    } catch (Exception e) {
      logger.error(e.getMessage());
    } finally {
      ErrorsMandatoryAtt.clear();
      ErrorsRevision.clear();
      erroMsgRemoved.clear();
      bomErrors.clear();
    }
    ErrorsMandatoryAtt.clear();
    ErrorsRevision.clear();
    erroMsgRemoved.clear();
    bomErrors.clear();
    
    return new EventActionResult(request, new ActionResult(0, "No Errors"));
  }
  
  private void checkMfrDiagram(IChange change) throws APIException
  {
    ITable tableAI = change.getTable(ChangeConstants.TABLE_AFFECTEDITEMS);
    Iterator itAI = tableAI.iterator();
    
    while (itAI.hasNext()) {
      logger.debug("In AI table");
      String PC = properties.getProperty("PC").toString();
      IRow row = (IRow)itAI.next();
      IItem affItem = (IItem)row.getReferent();
      String affItemNumb = row.getValue(ChangeConstants.ATT_AFFECTED_ITEMS_ITEM_NUMBER).toString();
      String affItemTyp = row.getValue(ChangeConstants.ATT_AFFECTED_ITEMS_ITEM_TYPE).toString();
      if (affItemTyp.equals(PC)) {
        ITable tableAttachments = affItem.getTable(ItemConstants.TABLE_ATTACHMENTS);
        Iterator itATT = tableAttachments.iterator();
        int counterATT = 0;
        while (itATT.hasNext()) {
          IRow rowAtt = (IRow)itATT.next();
          String attType = rowAtt.getValue(ItemConstants.ATT_ATTACHMENTS_ATTACHMENT_TYPE).toString();
          if (attType.equals("MFG Diagram")) {
            counterATT++;
          }
        }
        if (counterATT == 0) {
          message = "For " + affItemNumb + " : MFR Diagram is missing";
          ErrorsMandatoryAtt.add(message);
        }
      }
    }
  }
  



  private void checkRevision(IChange change, IAgileSession session)
    throws APIException
  {
    logger.debug("In Check Revision Method");
    String RAWF = properties.getProperty("ReviewApprovalWF").toString();
    String ICNWF = properties.getProperty("ICNWF").toString();
    String INumber = "";
    int counter = 0;
    String workflowCli = change.getValue(ChangeConstants.ATT_COVER_PAGE_WORKFLOW).toString();
    if ((workflowCli.equals(RAWF)) || (workflowCli.equals(ICNWF))) {
      ITable workflowTable = change.getTable(ChangeConstants.TABLE_WORKFLOW);
      Iterator itwf = workflowTable.iterator();
      while (itwf.hasNext()) {
        IRow rowwf = (IRow)itwf.next();
        logger.debug("status code " + rowwf.getValue(ChangeConstants.ATT_WORKFLOW_STATUS_CODE).toString());
        logger.debug("status action " + rowwf.getValue(ChangeConstants.ATT_WORKFLOW_ACTION).toString());
        logger.debug("status type " + change.getDefaultNextStatus().getStatusType().toString());
        
        if ((rowwf.getValue(ChangeConstants.ATT_WORKFLOW_STATUS_CODE).toString().trim().equals("Current Process")) && 
          (rowwf.getValue(ChangeConstants.ATT_WORKFLOW_ACTION).toString().trim().equals("Approved")) && 
          (change.getDefaultNextStatus().getStatusType().equals(StatusConstants.TYPE_RELEASED))) {
          counter++;
        }
      }
      if (counter == 0) {
        logger.debug("inside error for QA");
        messageRevision = "GPLM Change order should be approved by atleast one QA";
        ErrorsRevision.add(messageRevision);
        logger.debug("errror revision " + ErrorsRevision);
      }
    }
    ITable table = change.getTable(ChangeConstants.TABLE_AFFECTEDITEMS);
    Iterator it = table.iterator();
    while (it.hasNext())
    {
      IRow row = (IRow)it.next();
      ArrayList<Integer> list = new ArrayList();
      int min = 0;
      INumber = (String)row.getValue(ChangeConstants.ATT_AFFECTED_ITEMS_ITEM_NUMBER);
      IItem item = (IItem)session.getObject(2, INumber);
      ITable tab = item.getTable(ItemConstants.TABLE_PENDINGCHANGES);
      Iterator iter = tab.iterator();
      String propRev; while (iter.hasNext())
      {
        IRow row1 = (IRow)iter.next();
        propRev = (String)row1.getValue(ItemConstants.ATT_PENDING_CHANGES_PROPOSED_REV);
        if ((!propRev.equals(null)) && (!propRev.equals("")) && (!propRev.equals("(?)")))
        {
          propRev = propRev.replaceAll("\\p{P}", "");
          int Rev = Integer.parseInt(propRev);
          list.add(Integer.valueOf(Rev));
        }
      }
      min = ((Integer)list.get(0)).intValue();
      for (Integer i : list) {
        if (i.intValue() < min)
          min = i.intValue();
      }
      String rev = item.getRevision();
      if (rev.equals("Introductory")) {
        String nr = row.getValue(ChangeConstants.ATT_AFFECTED_ITEMS_NEW_REV).toString();
        int ink = Integer.parseInt(nr);
        if (ink == min) {
          if (ink != 1) {
            messageRevision = 
              "For :" + INumber + ": Please correct the proposed revision. Revision should be 1";
            ErrorsRevision.add(messageRevision);
          }
        } else {
          messageRevision = 
            "For " + INumber + ": There are previous revision(s) not released. Please release them before releasing this Change";
          ErrorsRevision.add(messageRevision);
        }
      } else {
        logger.info("inside else for checing the revision");
        int revInt = Integer.parseInt(rev);
        int nxtRev = revInt + 1;
        String nrev = row.getValue(ChangeConstants.ATT_AFFECTED_ITEMS_NEW_REV).toString();
        int ink = Integer.parseInt(nrev);
        if (ink == min) {
          if (ink != nxtRev) {
            logger.info("inside if for checkin the nextrev" + ink + "newrev" + nxtRev);
            messageRevision = "For " + INumber + 
              ": Please correct the proposed revision. Revision should be " + nxtRev;
            ErrorsRevision.add(messageRevision);
          }
        } else {
          messageRevision = 
            "For " + INumber + ": There are previous revision(s) not released. Please release them before releasing this Change";
          ErrorsRevision.add(messageRevision);
        }
      }
      list.clear();
    }
  }
  


  private void checkMandatoryAttributes(IChange change)
    throws APIException
  {
    logger.debug("In Check Mandatory attributes method");
    ITable table = change.getTable(ChangeConstants.TABLE_AFFECTEDITEMS);
    Iterator it = table.iterator();
    while (it.hasNext()) {
      IRow row = (IRow)it.next();
      String item = (String)row.getValue(ChangeConstants.ATT_AFFECTED_ITEMS_ITEM_NUMBER);
      IItem affItem = (IItem)row.getReferent();
      String Itemtype = affItem.getAgileClass().getAPIName();
      ITable redlineP2 = affItem.getTable(ItemConstants.TABLE_REDLINEPAGETWO);
      Iterator itr = redlineP2.getTableIterator();
      IRow redPage2Row = (IRow)itr.next();
      
      ICell ebsC = redPage2Row.getCell(ItemConstants.ATT_PAGE_TWO_LIST26);
      ICell cell = redPage2Row.getCell(CommonConstants.ATT_PAGE_TWO_LIST20);
      ICell mfrsn = redPage2Row.getCell(ItemConstants.ATT_PAGE_TWO_LIST27);
      ICell grade = redPage2Row.getCell(CommonConstants.ATT_PAGE_TWO_LIST21);
      ICell df = redPage2Row.getCell(CommonConstants.ATT_PAGE_TWO_LIST24);
      ICell cnu = redPage2Row.getCell(CommonConstants.ATT_PAGE_TWO_MULTITEXT43);
      ICell shipC = redPage2Row.getCell(CommonConstants.ATT_PAGE_TWO_LIST23);
      ICell soM = redPage2Row.getCell(CommonConstants.ATT_PAGE_TWO_LIST25);
      ICell storC = redPage2Row.getCell(CommonConstants.ATT_PAGE_TWO_LIST22);
      ICell specC = redPage2Row.getCell(CommonConstants.ATT_PAGE_TWO_TEXT16);
      ICell epD = redPage2Row.getCell(CommonConstants.ATT_PAGE_TWO_MULTITEXT42);
      ICell snU = redPage2Row.getCell(ItemConstants.ATT_PAGE_TWO_MULTITEXT46);
      ICell gileadCC = redPage2Row.getCell(CommonConstants.ATT_PAGE_TWO_TEXT14);
      ICell iupac = redPage2Row.getCell(CommonConstants.ATT_PAGE_TWO_MULTITEXT41);
      ICell pwnU = redPage2Row.getCell(CommonConstants.ATT_PAGE_TWO_TEXT07);
      ICell syno = redPage2Row.getCell(ItemConstants.ATT_PAGE_TWO_MULTITEXT47);
      ICell fc = redPage2Row.getCell(CommonConstants.ATT_PAGE_TWO_TEXT18);
      ICell packT = redPage2Row.getCell(ItemConstants.ATT_PAGE_TWO_LIST29);
      ICell placebo = redPage2Row.getCell(CommonConstants.ATT_PAGE_TWO_MULTILIST15);
      ICell SI = redPage2Row.getCell(CommonConstants.ATT_PAGE_TWO_TEXT08);
      ICell SO = redPage2Row.getCell(CommonConstants.ATT_PAGE_TWO_TEXT09);
      ICell FM = redPage2Row.getCell(CommonConstants.ATT_PAGE_TWO_MULTILIST10);
      ICell parIP = redPage2Row.getCell(ItemConstants.ATT_PAGE_TWO_LIST28);
      ICell cSN = redPage2Row.getCell(CommonConstants.ATT_PAGE_TWO_MULTITEXT41);
      ICell country = redPage2Row.getCell(CommonConstants.ATT_PAGE_TWO_MULTILIST12);
      ICell MPN = redPage2Row.getCell(CommonConstants.ATT_PAGE_TWO_MULTITEXT44);
      ICell lang = redPage2Row.getCell(CommonConstants.ATT_PAGE_TWO_MULTILIST02);
      switch (Itemtype) {
		case "RawMaterialRM":
			if (affItem.getValue(ItemConstants.ATT_TITLE_BLOCK_SIZE).toString().equals(null)
					|| affItem.getValue(ItemConstants.ATT_TITLE_BLOCK_SIZE).toString().equals("")) {
				message = "For " + item + " : Primary Unit of Measure cannot be empty";
				ErrorsMandatoryAtt.add(message);
			}
          if ((affItem.getValue(ItemConstants.ATT_TITLE_BLOCK_SIZE).toString().equals(null)) || 
            (affItem.getValue(ItemConstants.ATT_TITLE_BLOCK_SIZE).toString().equals(""))) {
            message = "For " + item + " : Primary Unit of Measure cannot be empty";
            ErrorsMandatoryAtt.add(message);
          }
          if ((ebsC.getValue().toString().equals(null)) || (ebsC.getValue().toString().equals(""))) {
            message = "For " + item + " : EBS Item Template cannot be empty";
            ErrorsMandatoryAtt.add(message);
          }
          if ((storC.getValue().toString().equals(null)) || (storC.getValue().toString().equals(""))) {
            message = "For " + item + " : Storage Condition cannot be empty";
            ErrorsMandatoryAtt.add(message);
            
          }
            break;
            
          case "InProcessIP":
            if ((affItem.getValue(ItemConstants.ATT_TITLE_BLOCK_SIZE).toString().equals(null)) || 
              (affItem.getValue(ItemConstants.ATT_TITLE_BLOCK_SIZE).toString().equals(""))) {
              message = "For " + item + " : Primary Unit of Measure cannot be empty";
              ErrorsMandatoryAtt.add(message);
            }
            if ((ebsC.getValue().toString().equals(null)) || (ebsC.getValue().toString().equals(""))) {
              message = "For " + item + " : EBS Item Template cannot be empty";
              ErrorsMandatoryAtt.add(message);
            }
            if ((shipC.getValue().toString().equals(null)) || (shipC.getValue().toString().equals(""))) {
              message = "For " + item + " : Shipping Condition cannot be empty";
              ErrorsMandatoryAtt.add(message);
            }
            if ((soM.getValue().toString().equals(null)) || (soM.getValue().toString().equals(""))) {
              message = "For " + item + " : Stage of Manufacturing cannot be empty";
              ErrorsMandatoryAtt.add(message);
            }
            if ((storC.getValue().toString().equals(null)) || (storC.getValue().toString().equals(""))) {
              message = "For " + item + " : Storage Condition cannot be empty";
              ErrorsMandatoryAtt.add(message);
            }
              break;
            case "PackagingConfigurationsPC":
              if ((affItem.getValue(ItemConstants.ATT_TITLE_BLOCK_SIZE).toString().equals(null)) || 
                (affItem.getValue(ItemConstants.ATT_TITLE_BLOCK_SIZE).toString().equals(""))) {
                message = "For " + item + " : Primary Unit of Measure cannot be empty";
                ErrorsMandatoryAtt.add(message);
              }
              /*
				 * if(ebsC.getValue().toString().equals(null)||ebsC.getValue().toString().equals
				 * ("")){ message = "For "+item+" : EBS Item Template cannot be empty" ;
				 * ErrorsMandatoryAtt.add(message);}
				 */
              if ((df.getValue().toString().equals(null)) || (df.getValue().toString().equals(""))) {
                message = "For " + item + " : Dosage Form cannot be empty";
                ErrorsMandatoryAtt.add(message);
              }
              if ((snU.getValue().toString().equals(null)) || (snU.getValue().toString().equals(""))) {
                message = "For " + item + " : Strength and Unit cannot be empty";
                ErrorsMandatoryAtt.add(message);
              }
              if ((cnu.getValue().toString().equals(null)) || (cnu.getValue().toString().equals(""))) {
                message = "For " + item + " : Concentration and Unit cannot be empty";
                ErrorsMandatoryAtt.add(message);
              }
              if ((shipC.getValue().toString().equals(null)) || (shipC.getValue().toString().equals(""))) {
                message = "For " + item + " : Shipping Condition cannot be empty";
                ErrorsMandatoryAtt.add(message);
              }
              if ((soM.getValue().toString().equals(null)) || (soM.getValue().toString().equals(""))) {
                message = "For " + item + " : Stage of Manufacturing cannot be empty";
                ErrorsMandatoryAtt.add(message);
              }
              if ((storC.getValue().toString().equals(null)) || (storC.getValue().toString().equals(""))) {
                message = "For " + item + " : Storage Condition cannot be empty";
                ErrorsMandatoryAtt.add(message);
              }
              if ((fc.getValue().toString().equals(null)) || (fc.getValue().toString().equals(""))) {
                message = "For " + item + " : Fill Count/Volume cannot be empty";
                ErrorsMandatoryAtt.add(message);
              }
              if ((pwnU.getValue().toString().equals(null)) || (pwnU.getValue().toString().equals(""))) {
                message = "For " + item + " : Percentage Weight and Unit cannot be empty";
                ErrorsMandatoryAtt.add(message);
              }
          break;

			case "FinishedProductFP":
          if ((affItem.getValue(ItemConstants.ATT_TITLE_BLOCK_SIZE).toString().equals(null)) || 
            (affItem.getValue(ItemConstants.ATT_TITLE_BLOCK_SIZE).toString().equals(""))) {
            message = "For " + item + " : Primary Unit of Measure cannot be empty";
            ErrorsMandatoryAtt.add(message);
          }
          
          /*
			 * if(ebsC.getValue().toString().equals(null)||ebsC.getValue().toString().equals
			 * ("")){ message = "For "+item+" : EBS Item Template cannot be empty" ;
			 * ErrorsMandatoryAtt.add(message);}
			 */
          if ((shipC.getValue().toString().equals(null)) || (shipC.getValue().toString().equals(""))) {
            message = "For " + item + " : Shipping Condition cannot be empty";
            ErrorsMandatoryAtt.add(message);
          }
          if ((soM.getValue().toString().equals(null)) || (soM.getValue().toString().equals(""))) {
            message = "For " + item + " : Stage of Manufacturing cannot be empty";
            ErrorsMandatoryAtt.add(message);
          }
          if ((storC.getValue().toString().equals(null)) || (storC.getValue().toString().equals(""))) {
            message = "For " + item + " : Storage Condition cannot be empty";
            ErrorsMandatoryAtt.add(message);
          }
          if ((fc.getValue().toString().equals(null)) || (fc.getValue().toString().equals(""))) {
            message = "For " + item + " : Fill Count/Volume cannot be empty";
            ErrorsMandatoryAtt.add(message);
          }
          if ((snU.getValue().toString().equals(null)) || (snU.getValue().toString().equals(""))) {
            message = "For " + item + " : Strength and Unit cannot be empty";
            ErrorsMandatoryAtt.add(message);
          }
          if ((cSN.getValue().toString().equals(null)) || (cSN.getValue().toString().equals(""))) {
            message = "For " + item + " : Clinical Study Number cannot be empty";
            ErrorsMandatoryAtt.add(message);
          }
          if ((country.getValue().toString().equals(null)) || (country.getValue().toString().equals(""))) {
            message = "For " + item + " : Country cannot be empty";
            ErrorsMandatoryAtt.add(message);
          }
          if ((lang.getValue().toString().equals(null)) || (lang.getValue().toString().equals(""))) {
            message = "For " + item + " : Languages cannot be empty";
            ErrorsMandatoryAtt.add(message);
          }
          break;

			case "CommercialAPIICN":          
            if ((epD.getValue().toString().equals(null)) || (epD.getValue().toString().equals(""))) {
              message = "For " + item + " : Extended Product Description cannot be empty";
              ErrorsMandatoryAtt.add(message);
            }
            if ((SI.getValue().toString().equals(null)) || (SI.getValue().toString().equals(""))) {
              message = "For " + item + " : Scale Input cannot be empty";
              ErrorsMandatoryAtt.add(message);
            }
            if ((SO.getValue().toString().equals(null)) || (SO.getValue().toString().equals(""))) {
              message = "For " + item + " : Scale Output cannot be empty";
              ErrorsMandatoryAtt.add(message);
            }
            if ((parIP.getValue().toString().equals(null)) || (parIP.getValue().toString().equals(""))) {
              message = "For " + item + " : Parent IP Part Number cannot be empty";
              ErrorsMandatoryAtt.add(message);
            }
            if ((MPN.getValue().toString().equals(null)) || (MPN.getValue().toString().equals(""))) {
              message = "For " + item + " : Manufacturing Process Name cannot be empty";
              ErrorsMandatoryAtt.add(message);
            }
            break;
			case "MedicalDeviceMD":
              if ((affItem.getValue(ItemConstants.ATT_TITLE_BLOCK_SIZE).toString().equals(null)) || 
                (affItem.getValue(ItemConstants.ATT_TITLE_BLOCK_SIZE).toString().equals(""))) {
                message = "For " + item + " : Primary Unit of Measure cannot be empty";
                ErrorsMandatoryAtt.add(message);
              }
              if ((shipC.getValue().toString().equals(null)) || (shipC.getValue().toString().equals(""))) {
                message = "For " + item + " : Shipping Condition cannot be empty";
                ErrorsMandatoryAtt.add(message);
              }
              if ((soM.getValue().toString().equals(null)) || (soM.getValue().toString().equals(""))) {
                message = "For " + item + " : Stage of Manufacturing cannot be empty";
                ErrorsMandatoryAtt.add(message);
              }
              if ((storC.getValue().toString().equals(null)) || (storC.getValue().toString().equals(""))) {
                message = "For " + item + " : Storage Condition cannot be empty";
                ErrorsMandatoryAtt.add(message);
              }

              break;
			case "CommercialBulk":
                if ((affItem.getValue(ItemConstants.ATT_TITLE_BLOCK_SIZE).toString().equals(null)) || 
                  (affItem.getValue(ItemConstants.ATT_TITLE_BLOCK_SIZE).toString().equals(""))) {
                  message = "For " + item + " : Primary Unit of Measure cannot be empty";
                  ErrorsMandatoryAtt.add(message);
                }
                if ((storC.getValue().toString().equals(null)) || (storC.getValue().toString().equals(""))) {
                  message = "For " + item + " : Storage Condition cannot be empty";
                  ErrorsMandatoryAtt.add(message);
                }
                break;
			case "CommercialBritestock":
                  if ((affItem.getValue(ItemConstants.ATT_TITLE_BLOCK_SIZE).toString().equals(null)) || 
                    (affItem.getValue(ItemConstants.ATT_TITLE_BLOCK_SIZE).toString().equals(""))) {
                    message = "For " + item + " : Primary Unit of Measure cannot be empty";
                    ErrorsMandatoryAtt.add(message);
                  }
                    
                  break;

			case "CommercialFinishedGoodFG":

                    if ((affItem.getValue(ItemConstants.ATT_TITLE_BLOCK_SIZE).toString().equals(null)) || 
                      (affItem.getValue(ItemConstants.ATT_TITLE_BLOCK_SIZE).toString().equals(""))) {
                      message = "For " + item + " : Primary Unit of Measure cannot be empty";
                      ErrorsMandatoryAtt.add(message);
                    }
                    if ((ebsC.getValue().toString().equals(null)) || (ebsC.getValue().toString().equals(""))) {
                      message = "For " + item + " : EBS Item Template cannot be empty";
                      ErrorsMandatoryAtt.add(message);
                    }

    				break;

    			case "CommercialWIP":

                      if ((affItem.getValue(ItemConstants.ATT_TITLE_BLOCK_SIZE).toString().equals(null)) || 
                        (affItem.getValue(ItemConstants.ATT_TITLE_BLOCK_SIZE).toString().equals(""))) {
                        message = "For " + item + " : Primary Unit of Measure cannot be empty";
                        ErrorsMandatoryAtt.add(message);
                      }
                     break;
      }
    }
  }
  
  private void printBOM(IItem affItem, String affItemN, String itemType) throws APIException {
    logger.debug("In Print BOM method for affected item" + affItemN);
    ITable table = affItem.getTable(ItemConstants.TABLE_BOM);
    Iterator it = table.iterator();
    
    while (it.hasNext()) {
      IRow row = (IRow)it.next();
      IItem bomNumber = (IItem)row.getReferent();
      checkPD(bomNumber, affItemN, itemType);
      IItem bomItem = (IItem)row.getReferent();
      printBOM(bomItem, affItemN, itemType);
    }
  }
  


  private void checkPD(IItem bomNumber, String affItemN, String itemType)
    throws APIException
  {
    logger.debug("In check PC method for " + affItemN);
    String IP = properties.getProperty("IP").toString();
    String PC = properties.getProperty("PC").toString();
    String Preliminary = properties.getProperty("Preliminary").toString();
    String Void = properties.getProperty("Void").toString();
    String Obsolete = properties.getProperty("Obsolete").toString();
    String bomNumberString = bomNumber.getValue(ItemConstants.ATT_TITLE_BLOCK_NUMBER).toString();
    String lifecycle = bomNumber.getValue(ItemConstants.ATT_TITLE_BLOCK_LIFECYCLE_PHASE).toString();
    ITable tablePC = bomNumber.getTable(ItemConstants.TABLE_PENDINGCHANGES);
    ArrayList<Integer> list = new ArrayList();
    int min = 0;
    int count = 0;
    Iterator pendChngforLeastRev = tablePC.iterator();
    IChange pendCHNG; while (pendChngforLeastRev.hasNext()) {
      IRow rowPC = (IRow)pendChngforLeastRev.next();
      pendCHNG = (IChange)rowPC.getReferent();
      IAgileClass classCheck = pendCHNG.getAgileClass();
      if (classCheck.isSubclassOf(ChangeConstants.CLASS_CHANGE_ORDERS_CLASS)) {
        count++;
        String propRev = (String)rowPC.getValue(ItemConstants.ATT_PENDING_CHANGES_PROPOSED_REV);
        if ((!propRev.equals(null)) && (!propRev.equals("")) && (!propRev.equals("(?)")))
        {
          propRev = propRev.replaceAll("\\p{P}", "");
          int Rev = Integer.parseInt(propRev);
          list.add(Integer.valueOf(Rev));
        }
      }
    }
    if ((tablePC.size() != 0) && (count != 0)) {
      min = ((Integer)list.get(0)).intValue();
      for (Integer i : list) {
        if (i.intValue() < min)
          min = i.intValue();
      }
    }
    logger.debug(Preliminary + Void + Obsolete + "Actual LC------" + lifecycle + "Child" + bomNumberString);
    String priliminaryinAI = "";
    int prirev = 0;
    if (lifecycle.equals(Preliminary)) {
      ITable table = bomNumber.getTable(ItemConstants.TABLE_PENDINGCHANGES);
      Iterator iteratorPendChng = table.iterator();
      if (table.size() != 0) {
        while (iteratorPendChng.hasNext()) {
          IRow rowPC = (IRow)iteratorPendChng.next();
          IChange pendingChange = (IChange)rowPC.getReferent();
          IAgileClass changeClass = pendingChange.getAgileClass();
          String changeNumber = rowPC.getValue(ItemConstants.ATT_PENDING_CHANGES_NUMBER).toString();
          String revision = (String)rowPC.getValue(ItemConstants.ATT_PENDING_CHANGES_PROPOSED_REV);
          logger.debug(changeNumber + " CHange and REv " + revision);
          int rev = 0;
          if ((!revision.isEmpty()) && (!revision.equals("( )")))
          {
            revision = revision.replaceAll("\\p{P}", "");
            rev = Integer.parseInt(revision);
          }
          if (changeClass.isSubclassOf(ChangeConstants.CLASS_CHANGE_ORDERS_CLASS))
          {
            if (changeNumber.equals(changeNumberActual)) {
              priliminaryinAI = new String(changeNumber);
              prirev = rev;
            }
          }
        }
      }
      else if ((itemType.equals(IP)) || (itemType.equals(PC))) {
        bomMessage = affItemN;
        bomErrors.add(bomMessage);
      }
    }
    
    if (!priliminaryinAI.equals("")) {
      logger.debug("Has pending change similar to that of Change");
      if (prirev != 1)
      {
        if ((itemType.equals(IP)) || (itemType.equals(PC))) {
          bomMessage = affItemN;
          bomErrors.add(bomMessage);
        }
      }
    }
    if ((lifecycle.equals(Void)) || (lifecycle.equals(Obsolete))) {
      bomMessage = affItemN;
      bomErrors.add(bomMessage);
    }
    ITable tableUR = bomNumber.getTable(ItemConstants.TABLE_PENDINGCHANGES);
    logger.debug("After LC check for -" + bomNumberString);
    if (tableUR.size() != 0) {
      Iterator iteratorPendChng = tableUR.iterator();
      String changePresentinAI = "";
      int RevinAI = 0;
      int countA = 0;
      while (iteratorPendChng.hasNext()) {
        IRow rowPC = (IRow)iteratorPendChng.next();
        IChange pendingChange = (IChange)rowPC.getReferent();
        IAgileClass changeClass = pendingChange.getAgileClass();
        String changeNumber = rowPC.getValue(ItemConstants.ATT_PENDING_CHANGES_NUMBER).toString();
        logger.debug(changeNumber + "this is the change number");
        String revision = rowPC.getValue(ItemConstants.ATT_PENDING_CHANGES_PROPOSED_REV).toString();
        logger.debug(revision + "Issue not with Revision");
        int Rev = 0;
        if ((!revision.isEmpty()) && (!revision.equals("( )")))
        {
          revision = revision.replaceAll("\\p{P}", "");
          Rev = Integer.parseInt(revision);
        }
        logger.debug("Table has pending change : " + pendingChange);
        if (changeClass.isSubclassOf(ChangeConstants.CLASS_CHANGE_ORDERS_CLASS)) {
          countA++;
          if (changeNumber.equals(changeNumberActual)) {
            logger.debug("CN---" + changeNumber + "Revis " + Rev);
            changePresentinAI = new String(changeNumber);
            RevinAI = Rev;
          }
        }
      }
      if (changePresentinAI.equals("")) {
        if ((countA != 0) && (
          (itemType.equals(IP)) || (itemType.equals(PC)))) {
          bomMessage = affItemN;
          bomErrors.add(bomMessage);
        }
        
      }
      else if (RevinAI != min)
      {
        if ((itemType.equals(IP)) || (itemType.equals(PC))) {
          bomMessage = affItemN;
          bomErrors.add(bomMessage);
        }
      }
    }
  }
}
