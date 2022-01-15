package esameTAAS.productMicroservice.Repositories;


import esameTAAS.productMicroservice.Models.Match;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MatchRepository extends JpaRepository<Match,Long> {
    List<Match> getMatchByUsername1OrUsername2(String username1,String username2);
}
