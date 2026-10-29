export type ActionType =
  | "SEND_INVOICE"
  | "SEND_REMINDER"
  | "REFUND"
  | "ADD_TRACKING"
  | "DISPUTE_RESPONSE";

export type ActionStatus = "PROPOSED" | "APPROVED" | "EXECUTED" | "REJECTED" | "FAILED";

export type InvoiceItem = {
  name?: string;
  description?: string;
  quantity?: string;
  unit_amount?: string;
};

export type InvoicePayload = {
  recipient_email?: string;
  recipient_given_name?: string;
  recipient_surname?: string;
  currency_code?: string;
  note?: string;
  items?: InvoiceItem[];
};

export type ReminderPayload = {
  invoice_id?: string;
  subject?: string;
  note?: string;
};

export type ActionView = {
  id: string;
  action_type: ActionType;
  status: ActionStatus;
  summary: string;
  payload: InvoicePayload | ReminderPayload | null;
  result_reference: string | null;
  error_message: string | null;
  created_at: string;
  updated_at: string;
};

export type AssistResponse = {
  message: string;
  proposed_actions: ActionView[];
  context_truncated: boolean;
};

export function isInvoicePayload(
  action: ActionView
): action is ActionView & { payload: InvoicePayload } {
  return action.action_type === "SEND_INVOICE" && action.payload !== null;
}

export function isReminderPayload(
  action: ActionView
): action is ActionView & { payload: ReminderPayload } {
  return action.action_type === "SEND_REMINDER" && action.payload !== null;
}

export function emptyItem(): InvoiceItem {
  return { name: "", quantity: "1", unit_amount: "0.00" };
}
