// Functions to format the time
export const formatTime = (dateString: string): string => {
  const date = new Date(dateString);
  let hours = date.getHours();
  let minutes = date.getMinutes();
  let hoursStr = hours < 10 ? "0" + hours : hours.toString();
  let minutesStr = minutes < 10 ? "0" + minutes : minutes.toString();

  return `${hoursStr}:${minutesStr}`;
};

export const formatTimeNewDate = (date: Date): string => {
  let hours = date.getHours();
  let minutes = date.getMinutes();
  let hoursStr = hours < 10 ? "0" + hours : hours;
  let minutesStr = minutes < 10 ? "0" + minutes : minutes;
  return `${hoursStr}:${minutesStr}`;
};

// Retrieve styles from the TextMessage Component based on the sender type
export const getMessageType = (sender: string) => {
  if (sender === "error") {
    return "error";
  } else if (sender === "user") {
    return "right-user";
  } else {
    return "left-user";
  }
};