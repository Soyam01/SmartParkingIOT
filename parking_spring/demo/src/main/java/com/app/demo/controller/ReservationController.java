//package com.app.demo.controller;
//
//import com.app.demo.model.Reservation;
//import com.app.demo.repository.ReservationRepository;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Controller;
//import org.springframework.ui.Model;
//import org.springframework.web.bind.annotation.*;
//
//import java.time.LocalDateTime;
//
//@Controller
//public class ReservationController {
//
//    @Autowired
//    private ReservationRepository repo;
//
//    @GetMapping("/")
//    public String home(Model model) {
//        model.addAttribute("reservations", repo.findAllByActiveTrue());
//        model.addAttribute("newReservation", new Reservation());
//        return "index";
//    }
//
//    @PostMapping("/reserve")
//    public String register(@ModelAttribute Reservation reservation) {
//        reservation.setPlateNumber(reservation.getPlateNumber().toUpperCase().replace(" ", ""));
//        reservation.setActive(true);
//        repo.save(reservation);
//        return "redirect:/";
//    }
//
//    // Later: endpoint for Python to check & mark matched
//    @PostMapping("/api/match")
//    @ResponseBody
//    public String markMatched(@RequestParam String plate) {
//        var opt = repo.findByPlateNumberIgnoreCaseAndActiveTrue(plate);
//        if (opt.isPresent()) {
//            Reservation r = opt.get();
//            r.setActive(false);
//            r.setMatchedAt(LocalDateTime.now());
//            repo.save(r);
//            return "MATCHED";
//        }
//        return "NOT_FOUND";
//    }
//}