package be.kuleuven.divingrestservice.exceptions;

public class ReservationException extends RuntimeException{

        public ReservationException() {
            super("No Reservation Made");
        }
        public ReservationException(String reservationNotFound) {
            super(reservationNotFound);
        }
}
