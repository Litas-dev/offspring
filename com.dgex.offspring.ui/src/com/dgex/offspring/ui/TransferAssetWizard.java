package com.dgex.offspring.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import nxt.Account;
import nxt.Asset;
import nxt.Constants;
import nxt.NxtException.ValidationException;
import nxt.Transaction;
import nxt.util.Convert;

import org.apache.log4j.Logger;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Text;

import com.dgex.offspring.nxtCore.service.IAccount;
import com.dgex.offspring.nxtCore.service.INxtService;
import com.dgex.offspring.nxtCore.service.TransactionException;
import com.dgex.offspring.nxtCore.service.Utils;
import com.dgex.offspring.swt.wizard.GenericTransactionWizard;
import com.dgex.offspring.swt.wizard.IGenericTransaction;
import com.dgex.offspring.swt.wizard.IGenericTransactionField;
import com.dgex.offspring.user.service.IUser;
import com.dgex.offspring.user.service.IUserService;

public class TransferAssetWizard extends GenericTransactionWizard {

  // In order to send an asset we need the following fields;
  //
  // String recipientValue = req.getParameter("recipient");
  // String assetValue = req.getParameter("asset");
  // String quantityValue = req.getParameter("quantity");
  //
  // Since a user can only send assets he actually owns the assetValue string is
  // selected from a dropdown that lists all assets owned by an account

  static Logger logger = Logger.getLogger(TransferAssetWizard.class);

  final IGenericTransactionField fieldRecipient = new IGenericTransactionField() {

    private Text textRecipient;
    private Text textRecipientReadonly;

    @Override
    public String getLabel() {
      return "Recipient";
    }

    @Override
    public Object getValue() {
      String recipientValue = textRecipient.getText().trim();
      try {
        return Convert.parseUnsignedLong(recipientValue);
      }
      catch (RuntimeException e) {
        logger.error("Parse Recipient ID", e);
      }
      return null;
    }

    @Override
    public Control createControl(Composite parent) {
      textRecipient = new Text(parent, SWT.BORDER);
      textRecipient.setText("");
      textRecipient.addModifyListener(new ModifyListener() {

        @Override
        public void modifyText(ModifyEvent e) {
          requestVerification();
        }
      });
      return textRecipient;
    }

    @Override
    public Control createReadonlyControl(Composite parent) {
      textRecipientReadonly = new Text(parent, SWT.BORDER);
      textRecipientReadonly.setText("");
      textRecipientReadonly.setEditable(false);
      return textRecipientReadonly;
    }

    @Override
    public boolean verify(String[] message) {
      String recipientValue = textRecipient.getText().trim();
      if ("0".equals(recipientValue)) {
        message[0] = "Missing recipient";
        return false;
      }

      try {
        Convert.parseUnsignedLong(recipientValue);
      }
      catch (RuntimeException e) {
        message[0] = "Incorrect recipient";
        return false;
      }
      textRecipientReadonly.setText(recipientValue);
      return true;
    }
  };

  final IGenericTransactionField fieldAsset = new IGenericTransactionField() {

    private final List<Asset> assets = new ArrayList<Asset>();
    private Combo comboAsset;
    private Combo comboAssetReadonly;
    private IUser currentUser;
    private final SelectionListener selectionListener = new SelectionAdapter() {

      @Override
      public void widgetSelected(SelectionEvent e) {
        requestVerification();
      }
    };

    @Override
    public String getLabel() {
      return "Asset";
    }

    @Override
    public Object getValue() {
      return assets.get(comboAsset.getSelectionIndex()); // Asset
    }

    private void populateAssets(Account account) {
      comboAsset.removeAll();
      assets.clear();
      Map<Long, Long> map = account.getAssetBalancesQNT();
      if (map != null) {
        for (Long assetId : map.keySet()) {
          Asset asset = Asset.getAsset(assetId);
          comboAsset.add(createLabel(account, asset));
          assets.add(asset);
        }
      }
    }

    private String createLabel(Account account, Asset asset) {
      Map<Long, Long> map = account.getAssetBalancesQNT();
      long balanceQNT = map != null ? map.get(asset.getId()) : 0l;
      return "Asset: " + asset.getName() + " Balance: "
          + Utils.quantToString(balanceQNT, asset.getDecimals());
    }

    @Override
    public Control createControl(Composite parent) {
      comboAsset = new Combo(parent, SWT.READ_ONLY);

      currentUser = (IUser) fieldSender.getValue();
      if (!currentUser.getAccount().isReadOnly()) {
        populateAssets(currentUser.getAccount().getNative());
      }
      comboAsset.select(0);
      comboAsset.addSelectionListener(selectionListener);
      return comboAsset;
    }

    @Override
    public Control createReadonlyControl(Composite parent) {
      comboAssetReadonly = new Combo(parent, SWT.READ_ONLY);
      // comboAssetReadonly.add("..");
      // comboAssetReadonly.select(0);
      return comboAssetReadonly;
    }

    @Override
    public boolean verify(String[] message) {

      /* Update readonly combo */
      Account account = user.getAccount().getNative();
      Asset asset = (Asset) getValue();
      comboAssetReadonly.removeAll();
      if (asset != null) {
        comboAssetReadonly.add(createLabel(account, asset));
        comboAssetReadonly.select(0);
      }

      /* There might have been a user change must update the list of assets */
      IUser sender = (IUser) fieldSender.getValue();
      if (!sender.equals(currentUser)) {
        currentUser = sender;
        comboAsset.removeAll();
        if (!sender.getAccount().isReadOnly()) {
          populateAssets(sender.getAccount().getNative());
        }
        comboAsset.removeSelectionListener(selectionListener);
        comboAsset.select(0);
        comboAsset.addSelectionListener(selectionListener);
      }
      return true;
    }
  };

  final IGenericTransactionField fieldQuantity = new IGenericTransactionField() {

    private Text textQuantity;
    private Text textQuantityReadonly;

    @Override
    public String getLabel() {
      return "Quantity";
    }

    @Override
    public Object getValue() {
      if (fieldAsset.getValue() == null)
        return null;
      return Utils.getQuantityQNT(textQuantity.getText().trim(),
          ((Asset) fieldAsset.getValue()).getDecimals());
    }

    @Override
    public Control createControl(Composite parent) {
      textQuantity = new Text(parent, SWT.BORDER);
      textQuantity.setText("0");
      textQuantity.addModifyListener(new ModifyListener() {

        @Override
        public void modifyText(ModifyEvent e) {
          requestVerification();
        }
      });
      return textQuantity;
    }

    @Override
    public Control createReadonlyControl(Composite parent) {
      textQuantityReadonly = new Text(parent, SWT.BORDER);
      textQuantityReadonly.setEditable(false);
      return textQuantityReadonly;
    }

    @Override
    public boolean verify(String[] message) {
      if (fieldAsset.getValue() == null) {
        message[0] = "Must set asset first";
        return false;
      }
      String quantityValue = textQuantity.getText().trim();
      Long quantityQNT = Utils.getQuantityQNT(quantityValue,
          ((Asset) fieldAsset.getValue()).getDecimals());
      if (quantityQNT == null) {
        message[0] = "Incorrect quantity";
        return false;
      }
      textQuantityReadonly.setText(quantityValue);
      return true;
    }
  };

  final IGenericTransactionField fieldComment = new IGenericTransactionField() {

    private Text textComment;
    private Text textCommentReadonly;

    @Override
    public String getLabel() {
      return "Comment";
    }

    @Override
    public Object getValue() {
      return textComment.getText().trim();
    }

    @Override
    public Control createControl(Composite parent) {
      textComment = new Text(parent, SWT.BORDER);
      textComment.setText("");
      textComment.addModifyListener(new ModifyListener() {

        @Override
        public void modifyText(ModifyEvent e) {
          requestVerification();
        }
      });
      return textComment;
    }

    @Override
    public Control createReadonlyControl(Composite parent) {
      textCommentReadonly = new Text(parent, SWT.BORDER);
      textCommentReadonly.setText("");
      textCommentReadonly.setEditable(false);
      return textCommentReadonly;
    }

    @Override
    public boolean verify(String[] message) {
      String comment = textComment.getText().trim();
      if (comment.length() > Constants.MAX_ASSET_TRANSFER_COMMENT_LENGTH) {
        message[0] = "Incorrect comment";
        return false;
      }
      textCommentReadonly.setText(comment);
      return true;
    }
  };

  public TransferAssetWizard(final IUserService userService,
      final INxtService nxt) {
    super(userService);
    setWindowTitle("Transfer Asset");
    setTransaction(new IGenericTransaction() {

      @Override
      public String sendTransaction(String[] message) {

        IAccount sender = user.getAccount();
        Long recipient = (Long) fieldRecipient.getValue();
        long quantityQNT = (Long) fieldQuantity.getValue();
        Asset asset = (Asset) fieldAsset.getValue();

        PromptFeeDeadline dialog = new PromptFeeDeadline(getShell());
        if (dialog.open() != Window.OK) {
          message[0] = "Invalid fee and deadline";
          return null;
        }
        long feeNQT = dialog.getFeeNQT();
        short deadline = dialog.getDeadline();

        try {
          Transaction t = nxt.createTransferAssetTransaction(sender, recipient,
              asset.getId(), quantityQNT, "", deadline, feeNQT, null);
          return t.getStringId();
        }
        catch (ValidationException e) {
          e.printStackTrace();
          message[0] = e.getMessage();
        }
        catch (TransactionException e) {
          e.printStackTrace();
          message[0] = e.getMessage();
        }
        return null;
      }

      @Override
      public IGenericTransactionField[] getFields() {
        return new IGenericTransactionField[] { fieldSender, fieldRecipient,
            fieldAsset, fieldQuantity, fieldComment };
      }

      @Override
      public boolean verifySender(String message[]) {
        if (user == null) {
          message[0] = "Invalid sender";
          return false;
        }
        if (user.getAccount().isReadOnly()) {
          message[0] = "This is a readonly account";
          return false;
        }

        Account account = user.getAccount().getNative();
        Asset asset = (Asset) fieldAsset.getValue();
        Long quantityQNT = (Long) fieldQuantity.getValue();
        if (asset != null && quantityQNT != null) {
          Long assetBalanceQNT = account.getUnconfirmedAssetBalanceQNT(asset
              .getId());
          if (assetBalanceQNT == null || quantityQNT > assetBalanceQNT) {
            message[0] = "Insufficient Asset Balance";
            return false;
          }
        }

        if (user.getAccount().getBalanceNQT() < Constants.ONE_NXT) {
          message[0] = "Insufficient Balance";
          return false;
        }
        return true;
      }
    });
  }
}
