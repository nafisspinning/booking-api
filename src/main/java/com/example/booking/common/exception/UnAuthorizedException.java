package com.example.booking.common.exception;

//Class yang digunakan untuk menangani exception ketika terjadi akses yang tidak sah atau tidak diizinkan. 
//Contoh: pengguna mencoba mengakses sumber daya yang memerlukan otorisasi tertentu, tetapi tidak memiliki hak akses yang sesuai.
public class UnAuthorizedException extends RuntimeException {
    public UnAuthorizedException(String message) {
        super(message);
    }   
}
