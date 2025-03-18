// Functions to format the time
export const formatTime = (dateString: string): string => {
  const date = new Date(dateString);
  let hours = date.getHours();
  let minutes = date.getMinutes();
  let hoursStr = hours < 10 ? "0" + hours : hours.toString();
  let minutesStr = minutes < 10 ? "0" + minutes : minutes.toString();

  return `${hoursStr}:${minutesStr}`;
};

// Convert time to hours:minutes
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

// Replace %s in the label with the provided value
// Reemplaza %s en el label por el valor proporcionado, solo si es posible
export const formatLabel = (label: string, count?: number): string | undefined => {
  if (label.includes('%s') && count !== undefined) {
    return label.replace('%s', String(count));
  }
  return undefined;
};

