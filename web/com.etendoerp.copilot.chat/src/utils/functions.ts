// Function to format the time
export const formatTime = (date: Date): string => {
  let hours = date.getHours();
  let minutes = date.getMinutes();
  let hoursStr = hours < 10 ? "0" + hours : hours;
  let minutesStr = minutes < 10 ? "0" + minutes : minutes;
  return `${hoursStr}:${minutesStr}`;
};
